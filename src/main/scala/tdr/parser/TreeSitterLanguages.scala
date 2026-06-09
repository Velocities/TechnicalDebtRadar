package tdr.parser

import org.treesitter.*

/** Maps a source-file extension to the tree-sitter grammar that parses it.
  *
  * The `TSLanguage` instances wrap immutable native grammar pointers, so they
  * are safe to share across threads and are created once, lazily. (A `TSParser`,
  * by contrast, is stateful and must not be shared — [[TreeSitterParser]] creates
  * one per parse.)
  */
private[parser] object TreeSitterLanguages:

  private lazy val scala      = new TreeSitterScala
  private lazy val java       = new TreeSitterJava
  private lazy val python     = new TreeSitterPython
  private lazy val go         = new TreeSitterGo
  private lazy val rust       = new TreeSitterRust
  private lazy val javascript = new TreeSitterJavascript
  private lazy val typescript = new TreeSitterTypescript
  private lazy val ruby       = new TreeSitterRuby
  private lazy val kotlin     = new TreeSitterKotlin
  private lazy val csharp     = new TreeSitterCSharp

  /** The grammar for `ext` (lower-cased, no leading dot), if we support it. */
  def forExt(ext: String): Option[TSLanguage] = ext.toLowerCase match
    case "scala" | "sc" | "sbt"     => Some(scala)
    case "java"                     => Some(java)
    case "py" | "pyi"               => Some(python)
    case "go"                       => Some(go)
    case "rs"                       => Some(rust)
    case "js" | "jsx" | "mjs" | "cjs" => Some(javascript)
    // .tsx is parsed with the TypeScript grammar (tree-sitter is error-tolerant
    // about the JSX bits, which we don't extract anyway).
    case "ts" | "tsx" | "mts" | "cts" => Some(typescript)
    case "rb"                       => Some(ruby)
    case "kt" | "kts"               => Some(kotlin)
    case "cs"                       => Some(csharp)
    case _                          => None
