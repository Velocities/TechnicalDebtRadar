package tdr.ir

/**
 * EntityIdGenerator defines a deterministic naming scheme for all graph nodes.
 *
 * WHY THIS EXISTS:
 * ----------------
 * In a codebase analysis tool, identity must be:
 *  - stable across runs
 *  - deterministic (same input → same ID)
 *  - human-readable (for debugging and PR diffs)
 *  - hierarchical (file → class → method → parameter)
 *
 * We intentionally DO NOT use:
 *  - UUIDs (non-deterministic, useless for diffs)
 *  - raw object references (not serializable)
 *
 * ID SCHEME:
 * ----------
 * File:
 *   <normalized-file-path>
 *
 * Class:
 *   <file-id>::class::<ClassName>
 *
 * Method:
 *   <class-id>::method::<methodName>(paramTypes):returnType
 *
 * Parameter:
 *   <method-id>::param::<index>
 *
 * This structure ensures:
 *  - uniqueness across entire repository
 *  - stable identity across refactors
 *  - easy diffing in PR analysis
 *  - readable debugging output
 */
object EntityIdGenerator:

  /** Normalize paths for cross-platform consistency */
  private def normalizePath(path: String): String =
    path.replace("\\", "/")

  /** FILE LEVEL ID */
  def fileId(absPath: String): EntityId =
    EntityId(normalizePath(absPath))

  /** CLASS LEVEL ID */
  def classId(file: EntityId, className: String): EntityId =
    EntityId(s"${file.value}::class::$className")

  /** METHOD LEVEL ID */
  def methodId(
    classId: EntityId,
    methodName: String,
    paramTypes: List[String],
    returnType: String
  ): EntityId =

    val params =
      if paramTypes.isEmpty then "()" 
      else paramTypes.mkString("(", ",", ")")

    EntityId(s"${classId.value}::method::$methodName$params:$returnType")

  /** PARAMETER LEVEL ID */
  def paramId(methodId: EntityId, index: Int): EntityId =
    EntityId(s"${methodId.value}::param::$index")