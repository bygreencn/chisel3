// See LICENSE for license details.

package Chisel

import internal._
import internal.Builder.pushCommand
import internal.firrtl._

import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context

object assert { // scalastyle:ignore object.name
  /** Checks for a condition to be valid in the circuit at all times. If the
    * condition evaluates to false, the circuit simulation stops with an error.
    *
    * Does not fire when in reset (defined as the encapsulating Module's
    * reset). If your definition of reset is not the encapsulating Module's
    * reset, you will need to gate this externally.
    *
    * May be called outside of a Module (like defined in a function), so
    * functions using assert make the standard Module assumptions (single clock
    * and single reset).
    *
    * @param cond condition, assertion fires (simulation fails) when false
    * @param message optional message to print when the assertion fires
    * @param data optional bits to print in the message formatting
    *
    * @note currently cannot be used in core Chisel / libraries because macro
    * defs need to be compiled first and the SBT project is not set up to do
    * that
    */
  def apply(cond: Bool, message: String, data: Bits*): Unit = macro apply_impl_msg_data
  def apply(cond: Bool): Unit = macro apply_impl  // macros currently can't take default arguments

  def apply_impl_msg_data(c: Context)(cond: c.Tree, message: c.Tree, data: c.Tree*): c.Tree = {
    import c.universe._
    val p = c.enclosingPosition
    val line = s"${p.source.file.name}:${p.line}"
    val apply_impl_do = symbolOf[this.type].asClass.module.info.member(TermName("apply_impl_do"))
    q"$apply_impl_do($cond, $line, $message, ..$data)"
 }

  def apply_impl(c: Context)(cond: c.Tree): c.Tree = {
    import c.universe._
    val p = c.enclosingPosition
    val line = s"${p.source.file.name}:${p.line}"
    val message = s"${p.lineContent.trim}"
    val apply_impl_do = symbolOf[this.type].asClass.module.info.member(TermName("apply_impl_do"))
    q"$apply_impl_do($cond, $line, $message)"
  }

  def apply_impl_do(cond: Bool, line: String, message: String, data: Bits*): Unit = {
    when (!(cond || Builder.dynamicContext.currentModule.get.reset)) {
      printf.printfWithoutReset(s"Assertion failed at $line: $message\n", data:_*)
      pushCommand(Stop(Node(Builder.dynamicContext.currentModule.get.clock), 1))
    }
  }


  /** An elaboration-time assertion, otherwise the same as the above run-time
    * assertion. */
  def apply(cond: Boolean, message: => String) {
    Predef.assert(cond, message)
  }

  /** A workaround for default-value overloading problems in Scala, just
    * 'assert(cond, "")' */
  def apply(cond: Boolean) {
    Predef.assert(cond, "")
  }
}

object printf { // scalastyle:ignore object.name
  /** Prints a message in simulation.
    *
    * Does not fire when in reset (defined as the encapsulating Module's
    * reset). If your definition of reset is not the encapsulating Module's
    * reset, you will need to gate this externally.
    *
    * May be called outside of a Module (like defined in a function), so
    * functions using printf make the standard Module assumptions (single clock
    * and single reset).
    *
    * @param fmt printf format string
    * @param data format string varargs containing data to print
    */
  def apply(fmt: String, data: Bits*) {
    when (!Builder.dynamicContext.currentModule.get.reset) {
      printfWithoutReset(fmt, data:_*)
    }
  }

  private[Chisel] def printfWithoutReset(fmt: String, data: Bits*) {
    val clock = Builder.dynamicContext.currentModule.get.clock
    pushCommand(Printf(Node(clock), fmt, data.map((d: Bits) => d.ref)))
  }
}
