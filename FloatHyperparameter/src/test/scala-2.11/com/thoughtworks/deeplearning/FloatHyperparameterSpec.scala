package com.thoughtworks.deeplearning

import java.util.logging.{Level, Logger}

import com.thoughtworks.deeplearning.math._
import com.thoughtworks.deeplearning.tapefactories.MultipleException
import com.thoughtworks.each.Monadic._
import com.thoughtworks.raii.asynchronous.Do
import com.thoughtworks.raii.covariant.{Releasable, ResourceT}
import com.thoughtworks.tryt.covariant.TryT
import org.scalactic.ErrorMessage
import org.scalatest._
import org.slf4j.bridge.SLF4JBridgeHandler
import com.thoughtworks.raii.asynchronous.Do._
import ResourceT._

import scalaz.concurrent.Future.futureInstance
import scala.concurrent.{ExecutionContext, Promise}
import scala.util.Try
import scalaz.concurrent.{Future, Task}
import scalaz.std.option._
import scalaz.{-\/, EitherT, MonadError, \/, \/-}
import scalaz.syntax.all._
import scalaz.std.`try`.toDisjunction
import scalaz.std.iterable._
import com.thoughtworks.feature.{Factory, The}
import Loss.train

/**
  * @author 杨博 (Yang Bo) &lt;pop.atry@gmail.com&gt;
  */
object FloatHyperparameterSpec {

  def predict[OutputData, OutputDelta](forward: Do[Tape[OutputData, OutputDelta]]): Task[OutputData] = {
    val Do(doOutputData) = forward.map(_.data)
    new Task(ResourceT.run(ResourceT(doOutputData).map(toDisjunction)))
  }

  trait FixedLearningRate extends LearningRate {
    def fixedLearningRate: scala.Float
    trait FloatOptimizerApi extends super.FloatOptimizerApi {
      final def learningRate: scala.Float = fixedLearningRate
    }
    override type FloatOptimizer <: FloatOptimizerApi
  }

  trait LearningRate extends FloatHyperparameter {
    trait FloatOptimizerApi extends super.FloatOptimizerApi {
      def learningRate: scala.Float
      override def delta: scala.Float = super.delta * learningRate
    }
    override type FloatOptimizer <: FloatOptimizerApi
  }

  trait L1Regularization extends FloatHyperparameter {
    def l1Regularization: scala.Float
    trait FloatOptimizerApi extends super.FloatOptimizerApi {
      override def delta: scala.Float = super.delta + scala.math.signum(weight.data) * l1Regularization
    }
    override type FloatOptimizer <: FloatOptimizerApi
  }
  trait L2Regularization extends FloatHyperparameter {
    def l2Regularization: scala.Float
    trait FloatOptimizerApi extends super.FloatOptimizerApi {
      override def delta: scala.Float = super.delta + weight.data * l2Regularization
    }
    override type FloatOptimizer <: FloatOptimizerApi
  }

  case class Boom(errorMessage: ErrorMessage) extends RuntimeException

  private def throwableFloatTapeTask(throwable: Throwable): Do[Tape[Float, Float]] = {
    import com.thoughtworks.raii.covariant.ResourceT._
    import scalaz.concurrent.Future._

    val value1: TryT[ResourceT[Future, `+?`], Tape[Float, Float]] = TryT
      .tryTMonadError[ResourceT[Future, `+?`]]
      .raiseError[Tape[Float, Float]](throwable)

    val value3: ResourceT[Future, Try[Tape[Float, Float]]] =
      TryT.unapply[ResourceT[Future, `+?`], Tape[Float, Float]](value1).get

    val value2: Future[Releasable[Future, Try[Tape[Float, Float]]]] =
      ResourceT.unapply(value3).get

    Do[Tape[Float, Float]](value2)
  }

  private def jump()(implicit executionContext: ExecutionContext): Task[Unit] = {
    Task.async { handler: ((Throwable \/ Unit) => Unit) =>
      executionContext.execute {
        new Runnable {
          override def run(): Unit = handler(\/-(()))
        }
      }
    }
  }
}

final class FloatHyperparameterSpec extends AsyncFreeSpec with Matchers with Inside {
  import FloatHyperparameterSpec._
  implicit val logger: java.util.logging.Logger = java.util.logging.Logger.getLogger("floatSpec")
  logger.setLevel(Level.ALL)
  SLF4JBridgeHandler.install()

  "Plus" in {

    val hyperparameters = Factory[FixedLearningRate].newInstance(logger = Logger.getGlobal, fixedLearningRate = 1.0f)
    import hyperparameters.implicits._

    val weight = hyperparameters.FloatWeight(1.0f)

    def myNetwork(input: Do[Tape[Float, Float]]): Do[Tape[Float, Float]] = {
      6.7f + input + weight + 5.5f
    }

    def train(inputData: Float): Task[Unit] = {
      import com.thoughtworks.raii.covariant.ResourceT._
      import scalaz.concurrent.Future._
      import com.thoughtworks.raii.asynchronous.Do.doMonadErrorInstances
      val c: Do[Unit] = myNetwork(Lift[Float].apply(inputData)).flatMap { outputTape: Tape[Float, Float] =>
        Do.delay(outputTape.backward(Do.now(1.0f)))
      }

      val Do(futureAsyncReleasable) = c

      val map: Future[\/[Throwable, Unit]] = ResourceT.run(ResourceT(futureAsyncReleasable)).map {
        toDisjunction
      }
      new Task(map)
    }

    val task: Task[Unit] = throwableMonadic[Task] {
      train(1.0f).each
      train(1.0f).each
      train(1.0f).each
      train(1.0f).each
      train(1.0f).each
    }

    val p = Promise[Assertion]
    task.unsafePerformAsync { either: \/[Throwable, Unit] =>
      p.success {
        inside(either) {
          case -\/(e) => throw e
          case \/-(_) => {
            weight.data should be(-4)
            true should be(true)
          }
        }
      }
    }
    p.future
  }

  "Plus with Train" in {
    val hyperparameters = Factory[FixedLearningRate].newInstance(logger = Logger.getGlobal, fixedLearningRate = 1.0f)
    import hyperparameters.implicits._

    val weight = hyperparameters.FloatWeight(1.0f)

    def myNetwork(input: Float): Do[Tape[Float, Float]] = {
      6.7f + input + weight + 5.5f
    }

    def trainMyNetwork(inputData: Float): Task[Float] = {
      train(myNetwork(inputData))
    }

    val task: Task[Unit] = throwableMonadic[Task] {
      trainMyNetwork(1.0f).each
      trainMyNetwork(1.0f).each
      trainMyNetwork(1.0f).each
      trainMyNetwork(1.0f).each
      trainMyNetwork(1.0f).each
    }

    val p = Promise[Assertion]
    task.unsafePerformAsync { either: \/[Throwable, Unit] =>
      p.success {
        inside(either) {
          case -\/(e) => throw e
          case \/-(_) => {
            weight.data should be(-4)
          }
        }
      }
    }
    p.future
  }

  "Plus with Predict" in {
    val hyperparameters = Factory[FixedLearningRate].newInstance(logger = Logger.getGlobal, fixedLearningRate = 1.0f)
    import hyperparameters.implicits._

    val weight = hyperparameters.FloatWeight(1.0f)

    def myNetwork(input: Float): Do[Tape[Float, Float]] = {
      1.0f + input + weight + 4.0f
    }

    def trainMyNetwork(inputData: Float): Task[Float] = {
      train(myNetwork(inputData))
    }

    val task: Task[Unit] = throwableMonadic[Task] {
      trainMyNetwork(1.0f).each
      trainMyNetwork(1.0f).each
      trainMyNetwork(1.0f).each
      trainMyNetwork(1.0f).each
      trainMyNetwork(1.0f).each
      trainMyNetwork(1.0f).each
      trainMyNetwork(1.0f).each
    }

    val result = throwableMonadic[Task] {
      task.each
      predict(myNetwork(1.0f)).each
    }

    val p = Promise[Assertion]

    result.unsafePerformAsync { either: \/[Throwable, Float] =>
      p.success {
        inside(either) {
          case -\/(e) => throw e
          case \/-(loss) =>
            loss should be(0.0f)
        }
      }
    }

    p.future
  }

  "Predict -- use for" in {
    val hyperparameters = Factory[FixedLearningRate].newInstance(logger = Logger.getGlobal, fixedLearningRate = 1.0f)
    import hyperparameters.implicits._

    val weight = hyperparameters.FloatWeight(1.0f)

    def myNetwork(input: Float): Do[Tape[Float, Float]] = {
      -10.0f + 20.0f - ((input - weight + 4.0f) * 2.0f / 2.0f)
      //10.0f - (input - weight + 4.0f) //6
    }

    def trainMyNetwork(inputData: Float): Task[Float] = {
      train(myNetwork(inputData))
    }
    import scalaz.concurrent.Future._
    import com.thoughtworks.raii.asynchronous.Do.doMonadErrorInstances

    @monadic[Task]
    val task: Task[Unit] = {
      for (_ <- 1 to 6) {
        trainMyNetwork(1.0f).each
      }
    }

    val result = throwableMonadic[Task] {
      task.each
      predict(myNetwork(1.0f)).each
    }

    val p = Promise[Assertion]

    result.unsafePerformAsync { either: \/[Throwable, Float] =>
      p.success {
        inside(either) {
          case -\/(e) => throw e
          case \/-(loss) =>
            loss should be(0.0f)
            weight.data should be(-5)
        }
      }
    }

    p.future
  }

  "Predict -- one exception" in {
    val hyperparameters =
      Factory[FixedLearningRate]
        .newInstance(logger = Logger.getGlobal, fixedLearningRate = 1.0f)
    import hyperparameters.implicits._

    val weight = hyperparameters.FloatWeight(1.0f)

    def myNetwork(input: Float): Do[Tape[Float, Float]] = {
      10.0f - ((input - weight + throwableFloatTapeTask(Boom("4.0f"))) * 2.0f / 2.0f)
    }

    def trainMyNetwork(inputData: Float): Task[Float] = {
      train(myNetwork(inputData))
    }

    val p = Promise[Assertion]

    recoverToSucceededIf[MultipleException] {
      p.future
    }

    @monadic[Task]
    val task: Task[Unit] = {
      for (_ <- 1 to 6) {
        trainMyNetwork(1.0f).each
      }
    }

    val result = throwableMonadic[Task] {
      task.each
      predict(myNetwork(1.0f)).each
    }

    result.unsafePerformAsync { either: \/[Throwable, Float] =>
      p.success {
        inside(either) {
          case -\/(e) =>
            e should be(a[Boom])
        }
      }
    }

    p.future
  }

  "Predict -- two exception" in {
    val hyperparameters =
      Factory[FixedLearningRate]
        .newInstance(logger = Logger.getGlobal, fixedLearningRate = 1.0f)
    import hyperparameters.implicits._

    val weight = hyperparameters.FloatWeight(1.0f)

    def myNetwork(input: Float): Do[Tape[Float, Float]] = {
      10.0f - ((input - throwableFloatTapeTask(Boom("weight"))
        + throwableFloatTapeTask(Boom("4.0f"))) * 2.0f / 2.0f)
    }

    def trainMyNetwork(inputData: Float): Task[Float] = {
      train(myNetwork(inputData))
    }

    @monadic[Task]
    val task: Task[Unit] = {
      for (_ <- 1 to 6) {
        trainMyNetwork(1.0f).each
      }
    }

    val result = throwableMonadic[Task] {
      task.each
      predict(myNetwork(1.0f)).each
    }

    val p = Promise[Assertion]

    result.unsafePerformAsync { either: \/[Throwable, Float] =>
      p.success {
        inside(either) {
          case -\/(e) => {
            e should be(a[MultipleException])
            inside(e) {
              case MultipleException(multipleException) => multipleException.size should be(2)
            }
          }
        }
      }
    }

    p.future
  }

  "Predict -- three exception" in {
    val hyperparameters =
      Factory[FixedLearningRate]
        .newInstance(logger = Logger.getGlobal, fixedLearningRate = 1.0f)
    import hyperparameters.implicits._

    val weight = hyperparameters.FloatWeight(1.0f)

    def myNetwork(input: Float): Do[Tape[Float, Float]] = {
      10.0f - ((input - throwableFloatTapeTask(Boom("weight"))
        + throwableFloatTapeTask(Boom("4.0f"))) * 2.0f / throwableFloatTapeTask(Boom("2.0f")))
    }

    def trainMyNetwork(inputData: Float): Task[Float] = {
      train(myNetwork(inputData))
    }

    @monadic[Task]
    val task: Task[Unit] = {
      for (_ <- 1 to 6) {
        trainMyNetwork(1.0f).each
      }
    }

    val result = throwableMonadic[Task] {
      task.each
      predict(myNetwork(1.0f)).each
    }

    val p = Promise[Assertion]

    result.unsafePerformAsync { either: \/[Throwable, Float] =>
      p.success {
        inside(either) {
          case -\/(e) => {
            e should be(a[MultipleException])
            inside(e) {
              case MultipleException(multipleException) => multipleException.size should be(3)
            }
          }
        }
      }
    }

    p.future
  }

  "will not stackOverFlow" in {
    val hyperparameters =
      Factory[FixedLearningRate]
        .newInstance(logger = Logger.getGlobal, fixedLearningRate = 1.0f)
    import hyperparameters.implicits._

    val weight = hyperparameters.FloatWeight(1.0f)

    def myNetwork(input: Float): Do[Tape[Float, Float]] = {
      -10.0f + 20.0f - ((input - weight + 4.0f) * 2.0f / 2.0f)
    }

    def trainMyNetwork(inputData: Float): Task[Float] = {
      train(myNetwork(inputData))
    }

    @monadic[Task]
    val task: Task[Unit] = {
      for (_ <- 1 to 1000) {
        Task.apply(()).each
        trainMyNetwork(1.0f).each
      }
    }

    val result = throwableMonadic[Task] {
      task.each
      predict(myNetwork(1.0f)).each
    }

    val p = Promise[Assertion]

    result.unsafePerformAsync { either: \/[Throwable, Float] =>
      p.success {
        inside(either) {
          case -\/(e) => throw e
          case \/-(loss) => true should be(true)
        }
      }
    }

    p.future
  }

  "min" in {
    val hyperparameters =
      Factory[FixedLearningRate]
        .newInstance(logger = Logger.getGlobal, fixedLearningRate = 1.0f)
    import hyperparameters.implicits._

    val weight = hyperparameters.FloatWeight(1.0f)

    def myNetwork(input: Float): Do[Tape[Float, Float]] = {
      5.0f - min(5.0f, weight)
    }

    def trainMyNetwork(inputData: Float): Task[Float] = {
      train(myNetwork(inputData))
    }

    @monadic[Task]
    val task: Task[Unit] = {
      for (_ <- 1 to 4) {
        trainMyNetwork(1.0f).each
      }
    }

    val result = throwableMonadic[Task] {
      task.each
      predict(myNetwork(1.0f)).each
    }

    val p = Promise[Assertion]

    result.unsafePerformAsync { either: Throwable \/ Float =>
      p.success {
        inside(either) {
          case -\/(e) => throw e
          case \/-(loss) =>
            loss should be(0.0f)
            weight.data should be(5)
        }
      }
    }

    p.future
  }

  "max" in {
    val hyperparameters =
      Factory[FixedLearningRate]
        .newInstance(logger = Logger.getGlobal, fixedLearningRate = 1.0f)
    import hyperparameters.implicits._

    val weight = hyperparameters.FloatWeight(1.0f)

    def myNetwork(input: Float): Do[Tape[Float, Float]] = {
      10.0f - max(0.0f, weight)
    }

    def trainMyNetwork(inputData: Float): Task[Float] = {
      train(myNetwork(inputData))
    }

    @monadic[Task]
    val task: Task[Unit] = {
      for (_ <- 1 to 9) {
        trainMyNetwork(1.0f).each
      }
    }

    val result = throwableMonadic[Task] {
      task.each
      predict(myNetwork(1.0f)).each
    }

    val p = Promise[Assertion]

    result.unsafePerformAsync { either: Throwable \/ Float =>
      p.success {
        inside(either) {
          case -\/(e) => throw e
          case \/-(loss) =>
            loss should be(0.0f)
            weight.data should be(10)
        }
      }
    }

    p.future
  }

  "log" in {
    val hyperparameters = Factory[FixedLearningRate].newInstance(logger = Logger.getGlobal, fixedLearningRate = 0.5f)
    import hyperparameters.implicits._

    val weight = hyperparameters.FloatWeight(1.0f)

    val log5 = scala.math.log(5).toFloat

    def myNetwork(input: Float): Do[Tape[Float, Float]] = {
      log5 - log(weight)
    }

    def trainMyNetwork(inputData: Float): Task[Float] = {
      train(myNetwork(inputData))
    }

    @monadic[Task]
    val task: Task[Unit] = {
      for (_ <- 1 to 23) {
        jump().each
        trainMyNetwork(1.0f).each
      }
    }

    val result = throwableMonadic[Task] {
      task.each
      predict(myNetwork(1.0f)).each
    }

    val p = Promise[Assertion]

    result.unsafePerformAsync { either: \/[Throwable, Float] =>
      p.success {
        inside(either) {
          case -\/(e) => throw e
          case \/-(loss) =>
            scala.math.abs(weight.data - 5) should be < 0.1f
            loss should be < 0.1f
        }
      }
    }

    p.future
  }

  "exp" in {
    val hyperparameters = Factory[FixedLearningRate].newInstance(logger = Logger.getGlobal, fixedLearningRate = 0.1f)
    import hyperparameters.implicits._

    val weight = hyperparameters.FloatWeight(1.0f)

    val exp3 = scala.math.exp(3).toFloat

    def myNetwork(input: Float): Do[Tape[Float, Float]] = {
      exp3 - exp(weight)
    }

    def trainMyNetwork(inputData: Float): Task[Float] = {
      train(myNetwork(inputData))
    }

    @monadic[Task]
    val task: Task[Unit] = {
      for (_ <- 1 to 4) {
        trainMyNetwork(1.0f).each
      }
    }

    val result = throwableMonadic[Task] {
      task.each
      predict(myNetwork(1.0f)).each
    }

    val p = Promise[Assertion]

    result.unsafePerformAsync { either: \/[Throwable, Float] =>
      p.success {
        inside(either) {
          case -\/(e) => throw e
          case \/-(loss) =>
            scala.math.abs(weight.data - 3) should be < 0.1f
            loss should be < 0.5f
        }
      }
    }

    p.future
  }

  "abs" in {
    val hyperparameters =
      Factory[FixedLearningRate]
        .newInstance(logger = Logger.getGlobal, fixedLearningRate = 1.0f)
    import hyperparameters.implicits._

    val weight = hyperparameters.FloatWeight(1.0f)

    def myNetwork(input: Float): Do[Tape[Float, Float]] = {
      5.0f - abs(weight)
    }

    def trainMyNetwork(inputData: Float): Task[Float] = {
      train(myNetwork(inputData))
    }

    @monadic[Task]
    val task: Task[Unit] = {
      for (_ <- 1 to 4) {
        trainMyNetwork(1.0f).each
      }
    }

    val result = throwableMonadic[Task] {
      task.each
      predict(myNetwork(1.0f)).each
    }

    val p = Promise[Assertion]

    result.unsafePerformAsync { either: \/[Throwable, Float] =>
      p.success {
        inside(either) {
          case -\/(e) => throw e
          case \/-(loss) =>
            weight.data should be(5.0f)
            loss should be(0)
        }
      }
    }
    p.future
  }

  "unary_-" in {
    val hyperparameters =
      Factory[FixedLearningRate]
        .newInstance(logger = Logger.getGlobal, fixedLearningRate = 1.0f)
    import hyperparameters.implicits._

    val weight = hyperparameters.FloatWeight(5.0f)

    def myNetwork(input: Float): Do[Tape[Float, Float]] = {
      abs(-weight)
    }

    def trainMyNetwork(inputData: Float): Task[Float] = {
      train(myNetwork(inputData))
    }

    @monadic[Task]
    val task: Task[Unit] = {
      for (_ <- 1 to 5) {
        trainMyNetwork(1.0f).each
      }
    }

    val result = throwableMonadic[Task] {
      task.each
      predict(myNetwork(1.0f)).each
    }

    val p = Promise[Assertion]

    result.unsafePerformAsync { either: \/[Throwable, Float] =>
      p.success {
        inside(either) {
          case -\/(e) => throw e
          case \/-(loss) =>
            weight.data should be(0.0f)
            loss should be(0)
        }
      }
    }
    p.future
  }
}
