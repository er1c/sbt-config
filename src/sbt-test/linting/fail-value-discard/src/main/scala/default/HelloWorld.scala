package default

object HelloWorld extends App {
  valueWithUnitDiscarded()
  println("Hello, world!")

  def valueWithUnitDiscarded(): Unit = {
    1 + 1
  }
}
