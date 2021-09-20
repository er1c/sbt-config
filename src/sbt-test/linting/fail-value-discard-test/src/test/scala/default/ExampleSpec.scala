package default

import collection.mutable.ArrayStack
import org.scalatest._
import flatspec._
import matchers._

class ExampleSpec extends AnyFlatSpec with should.Matchers {

  "A Stack" should "pop values in last-in-first-out order" in {
    // Stack is deprecated in 2.12, so this error should be suppressed by the plugin
    val stack = new ArrayStack[Int]
    stack.push(1)
    stack.push(2)
    stack.pop() should be(2)
    stack.pop() should be(1)
  }

  it should "throw NoSuchElementException if an empty stack is popped" in {
    val emptyStack = new ArrayStack[Int]
    a[RuntimeException] should be thrownBy {
      emptyStack.pop()
    }
  }

  it should "fail on discarded non-Unit value" in {
    valueWithUnitDiscarded()
  }

  // This should succeed in tests
  def valueWithUnitDiscarded(): Unit = {
    doSomething()
  }

  def doSomething(): Boolean = true
}
