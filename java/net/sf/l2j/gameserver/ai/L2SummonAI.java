/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package net.sf.l2j.gameserver.ai;

import static net.sf.l2j.gameserver.ai.CtrlIntention.AI_INTENTION_ACTIVE;
import static net.sf.l2j.gameserver.ai.CtrlIntention.AI_INTENTION_ATTACK;
import static net.sf.l2j.gameserver.ai.CtrlIntention.AI_INTENTION_FOLLOW;
import static net.sf.l2j.gameserver.ai.CtrlIntention.AI_INTENTION_IDLE;
import static net.sf.l2j.gameserver.ai.CtrlIntention.AI_INTENTION_MOVE_TO;
import net.sf.l2j.gameserver.model.L2Attackable;
import net.sf.l2j.gameserver.model.L2Summon;
import net.sf.l2j.gameserver.model.L2Character;
import net.sf.l2j.gameserver.model.L2Character.AIAccessor;

public class L2SummonAI extends L2CharacterAI
{

    private boolean _thinking; // to prevent recursive thinking
    private boolean _startFollow = ((L2Summon)_actor).getFollowStatus();

    public L2SummonAI(AIAccessor accessor)
    {
        super(accessor);
    }

    @Override
    protected void onIntentionIdle()
    {
        stopFollow();
        _startFollow = false;
        onIntentionActive();
    }

    @Override
    protected void onIntentionActive()
    {
        L2Summon summon = (L2Summon) _actor;
        if (_startFollow)
            setIntention(AI_INTENTION_FOLLOW, summon.getOwner());
        else super.onIntentionActive();
    }

    private void thinkAttack()
    {
        L2Character target = getAttackTarget();
        if (checkTargetLostOrDead(target))
        {
            setAttackTarget(null);
            return;
        }

        final int attackRange = _actor.getPhysicalAttackRange();
        final int totalRange = attackRange + _actor.getTemplate().collisionRadius + target.getTemplate().collisionRadius;

        // Out of range: start/keep following — do NOT call doAttack logic (mirrors Brproject thinkAttack).
        // Avoids competing MoveToPawn broadcasts that manifest as client-side micro-teleports.
        if (!_actor.isInsideRadius(target, totalRange, false, false))
        {
            if (!_actor.isMovementDisabled())
            {
                if (getFollowTarget() != target)
                    startFollow(target, totalRange);
            }
            return;
        }

        // In range: stop following so FollowTask does not keep firing MoveToPawn packets.
        // Brproject pattern: 2D range check, consistent with main range gate. The
        // _attackTimeToMove covering the full swing (set in doAttack) keeps movement
        // locked during attack, preventing MoveToPawn broadcasts that would cancel
        // the client-side animation.
        if (getFollowTarget() != null && _actor.isInsideRadius(target, totalRange, false, false))
            stopFollow();

        // Abort early if currently attacking or casting — queue next attack intention
        // (mirrors L2PlayerAI.thinkAttack pattern). The AI will re-enter thinkAttack
        // on EVT_READY_TO_ACT once the swing completes.
        if (_actor.isAttackingNow() || _actor.isCastingNow())
        {
        	clientActionFailed();
        	return;
        }

        if (_actor.isAttackingDisabled())
        {
        	clientActionFailed();
        	return;
        }

        // Final range check defensive layer
        if (!_actor.isInsideRadius(target, totalRange, false, false))
        {
            clientActionFailed();
            return;
        }

        if (_actor.isMoving())
            _actor.stopMove(null);
        _accessor.doAttack(getAttackTarget());
        return;
    }

    private void thinkCast()
    {
        L2Summon summon = (L2Summon) _actor;
        if (checkTargetLost(getCastTarget()))
        {
            setCastTarget(null);
            return;
        }
        boolean val = _startFollow;
        if (maybeMoveToPawn(getCastTarget(), _actor.getMagicalAttackRange(_skill))) return;
        clientStopMoving(null);
        summon.setFollowStatus(false);
        setIntention(AI_INTENTION_IDLE);
        _startFollow = val;
        _accessor.doCast(_skill);
        return;
    }

    private void thinkPickUp()
    {
        if (_actor.isAllSkillsDisabled()) return;
        if (checkTargetLost(getTarget())) return;
        if (maybeMoveToPawn(getTarget(), 36)) return;
        setIntention(AI_INTENTION_IDLE);
        ((L2Summon.AIAccessor) _accessor).doPickupItem(getTarget());
        return;
    }

    private void thinkInteract()
    {
        if (_actor.isAllSkillsDisabled()) return;
        if (checkTargetLost(getTarget())) return;
        if (maybeMoveToPawn(getTarget(), 36)) return;
        setIntention(AI_INTENTION_IDLE);
        return;
    }

    @Override
	protected void onEvtArrived()
    {
        _actor.revalidateZone();

        if (_actor.moveToNextRoutePoint())
            return;

        if (_actor instanceof L2Attackable)
            ((L2Attackable) _actor).setisReturningToSpawnPoint(false);

        clientStoppedMoving();

        if (getIntention() == AI_INTENTION_MOVE_TO)
            setIntention(AI_INTENTION_ACTIVE);
        else if (getIntention() == AI_INTENTION_ATTACK)
        {
            thinkAttack();
            return;
        }

        onEvtThink();
    }

    @Override
    protected void onEvtThink()
    {
        if (_thinking || _actor.isAllSkillsDisabled()) return;
        _thinking = true;
        try
        {
            switch(getIntention())
            {
                case AI_INTENTION_ATTACK:
                    thinkAttack();
                    break;
                case AI_INTENTION_CAST:
                    thinkCast();
                    break;
                case AI_INTENTION_PICK_UP:
                    thinkPickUp();
                    break;
                case AI_INTENTION_INTERACT:
                    thinkInteract();
                    break;
            }
        }
        finally
        {
            _thinking = false;
        }
    }

    @Override
    protected void onEvtFinishCasting()
    {
    	((L2Summon)_actor).setFollowStatus(_startFollow);
    }

    public void notifyFollowStatusChange()
    {
        _startFollow = !_startFollow;
        switch (getIntention())
        {
        	case AI_INTENTION_ACTIVE:
        	case AI_INTENTION_FOLLOW:
        	case AI_INTENTION_IDLE:
        		((L2Summon)_actor).setFollowStatus(_startFollow);
        }
    }

    public void setStartFollowController(boolean val)
    {
    	_startFollow = val;
    }
}
