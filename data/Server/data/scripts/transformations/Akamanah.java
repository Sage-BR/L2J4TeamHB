package transformations;

import net.sf.l2j.gameserver.instancemanager.TransformationManager;
import net.sf.l2j.gameserver.model.L2Transformation;

/**
 * This is currently only a test of the java script engine
 *
 * @author durgus
 *
 */
public class Akamanah extends L2Transformation
{
	public Akamanah()
	{
		// id, duration (secs), colRadius, colHeight
		super(302, Integer.MAX_VALUE, 10.0, 32.73);
	}

	@Override
	public void onTransform()
	{
		// Set charachter name to transformed name
		this.getPlayer().getAppearance().setVisibleName("Akamanah");
		this.getPlayer().getAppearance().setVisibleTitle("");
	}

	@Override
	public void onUntransform()
	{
		// set character back to true name.
		this.getPlayer().getAppearance().setVisibleName(null);
		this.getPlayer().getAppearance().setVisibleTitle(null);
	}

	public static void main(String[] args)
	{
		TransformationManager.getInstance().registerTransformation(new Akamanah());
	}
}
