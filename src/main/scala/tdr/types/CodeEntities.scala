package tdr.types

// Note: This is parameters for a function or method, NOT the parameters of the class
final case class ParameterEntity(
  name: String,
  dataType: String
)

// Note: This is a method of a class
final case class MethodEntity(
  name: String,
  parameters: List[ParameterEntity],
  returnDataType: String
)

// Note: This is a field of a class
final case class FieldEntity(
  name: String,
  dataType: String
)

// Classes and Functions (named "entities" to avoid confusion with the class and function keywords)
final case class ClassEntity(
  name: String,
  // Methods the class holds on itself
  methods: List[MethodEntity],
  // Attributes the class holds on itself
  fields: List[FieldEntity]
)

final case class FunctionEntity(
  name: String,
  // Parameters the function takes
  parameters: List[ParameterEntity],
  // Could sometimes be void, of course
  returnDataType: Option[String]
)
