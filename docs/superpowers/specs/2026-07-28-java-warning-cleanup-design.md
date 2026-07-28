# Java warning cleanup

Clean the reported static-analysis findings without changing game behavior. Remove unused imports, private fields, local variables, and private members whose repository-wide references are absent. Simplify redundant casts and obsolete warning suppressions. Rename subclass fields that hide inherited state. Correct the dimensional-rift map lookup to use a `Byte` key.

Public or protected compatibility surfaces remain unless their removal is demonstrably safe. Validate with the Ant compile target after edits.
