//package com.thoughtworks.deeplearning.plugins
//
//import java.util.logging.{Level, Logger}
//
//import com.thoughtworks.deeplearning.logs.{UncaughtExceptionDuringBackward, WeightIsUpdating}
//import com.thoughtworks.deeplearning.math._
//import com.thoughtworks.deeplearning.math.polyFunctions
//import com.thoughtworks.deeplearning.Tape
//import com.thoughtworks.deeplearning.tapefactories.{MonoidOutput, SemigroupOutput, Unary}
//import com.thoughtworks.deeplearning.Lift.LowPriorityLift
//import com.thoughtworks.deeplearning._
//import com.thoughtworks.raii.asynchronous.Do
//import com.thoughtworks.raii.asynchronous.Do._
//import com.thoughtworks.raii.covariant.{Releasable, ResourceT}
//import org.nd4j.linalg.api.ops.impl.transforms.{IsMax, Sqrt}
//import org.nd4j.linalg.convolution.Convolution
//import org.nd4j.linalg.factory.Nd4j
//import org.nd4j.linalg.indexing.BooleanIndexing
//import org.nd4j.linalg.indexing.conditions.Conditions
//import org.nd4j.linalg.ops.transforms.Transforms
//import org.nd4j.linalg.ops.transforms.Transforms.{sign, sqrt}
//import org.nd4j.linalg.util.ArrayUtil
//import org.nd4s.Implicits._
//import com.thoughtworks.each.Monadic._
//import com.thoughtworks.raii.asynchronous
//import shapeless.{HList, Lazy, Poly0, Witness, the}
//import com.thoughtworks.deeplearning.math._
//import com.thoughtworks.feature._
//import com.thoughtworks.feature.byname.ByName
//import com.thoughtworks.feature.Factory.inject
//
//import scala.concurrent.ExecutionContext
//import scala.util.{Failure, Success, Try}
//import scalaz.{-\/, Monoid, Semigroup, \/, \/-}
//import scalaz.concurrent.{Future, Task}
//import scalaz.syntax.all._
//import scala.{Double => ScalaDouble}
//import org.nd4j.linalg.api.ndarray.{INDArray => ND4JArray}
//import shapeless.PolyDefns.Case0
//import shapeless.ops.hlist.Selector
//import sourcecode.{FullName, Name}
//
//import scala.annotation.meta.getter
//
//object indarray {
//
//  private def jumpTask()(implicit executionContext: ExecutionContext): Task[Unit] = {
//    Task.async { handler: ((Throwable \/ Unit) => Unit) =>
//      executionContext.execute {
//        new Runnable {
//          override def run(): Unit = handler(\/-(()))
//        }
//      }
//    }
//  }
//
//  trait Weight {
//
//    /** The function type to create an instance that extends [[Optimizer]] */
//    private[Weight] type OptimizerFunction
//
//    private[Weight] type Optimizer <: Hyperparameter#INDArrayOptimizer
//    protected[Weight] def forward[SubtypeOfOptimizer](
//        implicit implicitApplyRest: ImplicitApply.Aux[OptimizerFunction, SubtypeOfOptimizer],
//        constraint: SubtypeOfOptimizer <:< Optimizer): Do[Tape[ND4JArray, ND4JArray]]
//  }
//
//  object Weight {
//
//    private[Weight] type Aux[OptimizerFunction0, Optimizer0] = Weight {
//      type OptimizerFunction = OptimizerFunction0
//      type Optimizer = Optimizer0
//    }
//
//    implicit def liftWeight[From, OptimizerFunction, Optimizer, SubtypeOfOptimizer](
//        implicit weightConstraint: From <:< Weight.Aux[OptimizerFunction, Optimizer],
//        implicitApply: ImplicitApply.Aux[OptimizerFunction, SubtypeOfOptimizer],
//        outConstraint: SubtypeOfOptimizer <:< Optimizer) = new LowPriorityLift[From] {
//      override def apply(from: From): Do[Tape[Data, Delta]] = {
//        weightConstraint(from).forward[SubtypeOfOptimizer](implicitApply, outConstraint)
//      }
//
//      override type Data = ND4JArray
//      override type Delta = ND4JArray
//    }
//  }
//
//  trait Hyperparameter {
//
//    trait Weight extends indarray.Weight { this: INDArrayWeight =>
//      override type OptimizerFunction = partialApplyOriginalDelta.Rest
//      override type Optimizer = INDArrayOptimizer
//      implicit val logger: Logger
//      implicit val fullName: sourcecode.FullName
//      implicit val name: sourcecode.Name
//      implicit val caller: Caller[_]
//      implicit val executionContext: ExecutionContext
//
//      var data: ND4JArray
//
//      private def optimizer[SubtypeOfOptimizer](delta: ND4JArray)(
//          implicit implicitApplyRest: ImplicitApply.Aux[partialApplyOriginalDelta.Rest, SubtypeOfOptimizer],
//          constraint: SubtypeOfOptimizer <:< INDArrayOptimizer
//      ): INDArrayOptimizer = {
//        constraint(
//          implicitApplyRest(
//            partialApplyOriginalDelta(partialApplyWeight(optimizerFactory.newInstance, weightParameter(this)),
//                                      originalDeltaParameter(delta))))
//      }
//
//      override final def forward[SubtypeOfOptimizer](
//          implicit implicitApplyRest: ImplicitApply.Aux[partialApplyOriginalDelta.Rest, SubtypeOfOptimizer],
//          constraint: SubtypeOfOptimizer <:< INDArrayOptimizer): Do[Tape[ND4JArray, ND4JArray]] = {
//        Do.now(Tape(data, backward[SubtypeOfOptimizer]))
//      }
//
//      private final def backward[SubtypeOfOptimizer](deltaFuture: Do[ND4JArray])(
//          implicit implicitApplyRest: ImplicitApply.Aux[partialApplyOriginalDelta.Rest, SubtypeOfOptimizer],
//          constraint: SubtypeOfOptimizer <:< INDArrayOptimizer): Future[Unit] = {
//
//        Do.run(Do.releaseFlatMap(deltaFuture) { delta =>
//            Do.jump().map { unit: Unit =>
//              data -= optimizer(delta).delta
//              ()
//            }
//          })
//          .get
//          .map {
//            case \/-(()) => ()
//            case -\/(e) =>
//              val logRecord = UncaughtExceptionDuringBackward(e)
//              logger.log(logRecord)
//          }
//
//      }
//
//    }
//
//    type INDArrayWeight <: Weight
//    trait Optimizer {
//
//      val weight: INDArrayWeight
//
//      val originalDelta: ND4JArray
//      def delta: ND4JArray = originalDelta
//    }
//    type INDArrayOptimizer <: Optimizer
//
//    @(inject @getter)
//    val optimizerFactory: Factory[INDArrayOptimizer]
//
//    @(inject @getter)
//    val partialApplyWeight: PartialApply[optimizerFactory.Constructor, Witness.`"weight"`.T]
//
//    @(inject @getter)
//    val weightParameter: INDArrayWeight <:< partialApplyWeight.Parameter
//
//    @(inject @getter)
//    val partialApplyOriginalDelta: PartialApply[partialApplyWeight.Rest, Witness.`"originalDelta"`.T]
//
//    @(inject @getter)
//    val originalDeltaParameter: ND4JArray <:< partialApplyOriginalDelta.Parameter
//
//    @(inject @getter)
//    val weightFactory: Factory[INDArrayWeight]
//
//    @(inject @getter)
//    val partialApplyData: PartialApply[weightFactory.Constructor, Witness.`"data"`.T]
//
//    @(inject @getter)
//    val dataParameter: ND4JArray <:< partialApplyData.Parameter
//
//    def indArrayWeight[SubtypeOfWeight, OptimizerFunction, Optimizer](data: ND4JArray)(
//        implicit implicitApplyRest: ImplicitApply.Aux[partialApplyData.Rest, SubtypeOfWeight]
//    ): SubtypeOfWeight = {
//      implicitApplyRest(partialApplyData(weightFactory.newInstance, dataParameter(data)))
//    }
//
//  }
//
//  object hyperparameters {
//
//    trait FixedLearningRate extends LearningRate {
//      val fixedLearningRate: scala.Double
//      trait Optimizer extends super.Optimizer {
//        final def learningRate: scala.Double = fixedLearningRate
//      }
//      override type INDArrayOptimizer <: Optimizer
//    }
//
//    trait LearningRate extends Hyperparameter {
//      trait Optimizer extends super.Optimizer {
//        def learningRate: scala.Double
//        abstract override def delta: ND4JArray = super.delta * learningRate
//      }
//      override type INDArrayOptimizer <: Optimizer
//    }
//
//    trait L1Regularization extends Hyperparameter {
//      def l1Regularization: ND4JArray
//      trait Optimizer extends super.Optimizer {
//        abstract override def delta: ND4JArray = super.delta + sign(weight.data) * l1Regularization
//      }
//      override type INDArrayOptimizer <: Optimizer
//    }
//    trait L2Regularization extends Hyperparameter {
//      def l2Regularization: ND4JArray
//      trait Optimizer extends super.Optimizer {
//        abstract override def delta: ND4JArray = super.delta + weight.data * l2Regularization
//      }
//      override type INDArrayOptimizer <: Optimizer
//    }
//
//    trait Momentum extends Hyperparameter {
//      trait Weight extends super.Weight { this: INDArrayWeight =>
//        def mu: ScalaDouble = 0.9
//        var v: ND4JArray = Nd4j.zeros(data.shape: _*)
//      }
//
//      override type INDArrayWeight <: Weight
//
//      trait Optimizer extends super.Optimizer {
//
//        private lazy val delta0: ND4JArray = {
//          import weight._
//          v = super.delta + v * mu
//          v
//        }
//        abstract override def delta: ND4JArray = delta0
//      }
//      override type INDArrayOptimizer <: Optimizer
//    }
//
//    trait NesterovMomentum extends Momentum {
//      trait Optimizer extends super.Optimizer {
//        abstract override lazy val delta: ND4JArray = {
//          import weight._
//          val vPrev = v
//          vPrev * (-mu) + super.delta * (1 + mu)
//        }
//      }
//      override type INDArrayOptimizer <: super.Optimizer with Optimizer
//    }
//
//    /**
//      * @note This [[Adagrad]] hyperparameter is usually used before global [[LearningRate]]. e.g. `Adagrad with FixedLearningRate`, not `FixedLearningRate with Adagrad`
//      */
//    trait Adagrad extends Hyperparameter {
//      trait Weight extends super.Weight { this: INDArrayWeight =>
//        var cache: ND4JArray = Nd4j.zeros(data.shape: _*)
//      }
//
//      override type INDArrayWeight <: Weight
//
//      trait Optimizer extends super.Optimizer {
//        def eps: ScalaDouble = 1e-4
//
//        abstract override lazy val delta: ND4JArray = {
//          import weight._
//          cache = cache + super.delta * super.delta
//          super.delta / (sqrt(cache) + eps)
//        }
//      }
//      override type INDArrayOptimizer <: Optimizer
//    }
//
//    /**
//      * @note This [[RMSprop]] hyperparameter is usually used before global [[LearningRate]]. e.g. `RMSprop with FixedLearningRate`, not `FixedLearningRate with RMSprop`
//      */
//    trait RMSprop extends Hyperparameter {
//      trait Weight extends super.Weight { this: INDArrayWeight =>
//        var cache: ND4JArray = Nd4j.zeros(data.shape: _*)
//      }
//
//      override type INDArrayWeight <: Weight
//
//      trait Optimizer extends super.Optimizer {
//        def eps: ScalaDouble = 1e-4
//        def decayRate: ScalaDouble = 0.99
//        abstract override lazy val delta: ND4JArray = {
//          import weight._
//          cache = cache * decayRate + super.delta * super.delta * (1.0 - decayRate)
//          super.delta / (sqrt(cache) + eps)
//        }
//      }
//      override type INDArrayOptimizer <: Optimizer
//    }
//
//    trait Adam extends Hyperparameter {
//
//      trait Weight extends super.Weight { this: INDArrayWeight =>
//        var m: ND4JArray = Nd4j.zeros(data.shape: _*)
//        var v: ND4JArray = Nd4j.zeros(data.shape: _*)
//      }
//
//      override type INDArrayWeight <: Weight
//
//      trait Optimizer extends super.Optimizer {
//        def beta1: ScalaDouble = 0.9
//        def beta2: ScalaDouble = 0.999
//        def eps: ScalaDouble = 1e-8
//        abstract override lazy val delta: ND4JArray = {
//          import weight._
//          m = m * beta1 + super.delta * (1.0 - beta1)
//          v = v * beta2 + (super.delta * super.delta) * (1.0 - beta2)
//          m / (sqrt(v) + eps)
//        }
//      }
//      override type INDArrayOptimizer <: Optimizer
//    }
//  }
//
//  // TODO: Add a test for this method and auto-broadcasting on n-dimension arrays for n > 2
//  private[plugins] def sumAs(outputDeltaValue: ND4JArray, shape: Array[Int]) = {
//    val singleElementDimension = (shape: Seq[Int]).view.zip(outputDeltaValue.shape).zipWithIndex.collect {
//      case ((1, originSize), dimension) if originSize > 1 => dimension
//    }
//    if (singleElementDimension.isEmpty) {
//      outputDeltaValue
//    } else {
//      outputDeltaValue.sum(singleElementDimension.force: _*).reshape(shape: _*)
//    }
//  }
//
//  private[plugins] def autoBroadcastShape(shape1: Array[Int], shape2: Array[Int]) = {
//    require(shape1.length == shape2.length)
//    shape1.zip(shape2).map {
//      case (1, bSize) => bSize
//      case (aSize, 1) => aSize
//      case (aSize, bSize) if aSize == bSize => aSize
//    }
//  }
//
//  object implicits {
//    import com.thoughtworks.deeplearning.tapefactories.Binary.semigroupBinary
//    import com.thoughtworks.deeplearning.tapefactories.Unary.semigroupUnary
//
//    private implicit object INDArraySemigroup extends Semigroup[ND4JArray] {
//      override def append(f1: ND4JArray, f2: => ND4JArray): ND4JArray = f1 + f2
//    }
//
//    @inline
//    implicit def liftINDArray[A](
//        implicit typeClass: LowPriorityLift.Aux[A, ND4JArray, ND4JArray]): Lift.Aux[A, ND4JArray, ND4JArray] =
//      typeClass
//
//    implicit object INDArrayTrainable extends Loss[ND4JArray, ND4JArray] {
//      override def apply(data: ND4JArray): Do[ND4JArray] = Do.now(Nd4j.ones(data.shape(): _*))
//    }
//
//    @inline
//    implicit def `INDArray+INDArray`(
//        implicit logger: Logger = Logger.getGlobal,
//        fullName: sourcecode.FullName,
//        caller: Caller[_],
//        methodName: sourcecode.Name,
//        executionContext: ExecutionContext): polyFunctions.+.Case.Aux[Do[Tape[ND4JArray, ND4JArray]],
//                                                                      Do[Tape[ND4JArray, ND4JArray]],
//                                                                      Do[Tape[ND4JArray, ND4JArray]]] = {
//      polyFunctions.+.at { (operand0, operand1) =>
//        tapefactories.Binary.doTape(operand0, operand1) { (data0: ND4JArray, data1: ND4JArray) =>
//          throwableMonadic[Task] {
//            jumpTask().each
//            val outputData = {
//              val newShape = autoBroadcastShape(data0.shape(), data1.shape())
//              data0.broadcast(newShape: _*) + data1.broadcast(newShape: _*)
//            }
//            val computeBackward = { outputDelta: ND4JArray =>
//              val delta0Future = throwableMonadic[Do] {
//                Do.jump().each
//                sumAs(outputDelta, data0.shape())
//              }
//              val delta1Future = throwableMonadic[Do] {
//                Do.jump().each
//                sumAs(outputDelta, data1.shape())
//              }
//              (delta0Future, delta1Future)
//            }
//            (outputData, computeBackward)
//          }
//        }
//      }
//    }
//
//    @inline
//    implicit def `INDArray+Double`(implicit logger: Logger = Logger.getGlobal,
//                                   fullName: sourcecode.FullName,
//                                   caller: Caller[_],
//                                   methodName: sourcecode.Name,
//                                   executionContext: ExecutionContext)
//      : polyFunctions.+.Case.Aux[Do[Tape[ND4JArray, ND4JArray]], Do[DoubleTape], Do[Tape[ND4JArray, ND4JArray]]] = {
//      polyFunctions.+.at { (operand0, operand1) =>
//        tapefactories.Binary.doTape(operand0, operand1) { (data0: ND4JArray, data1: ScalaDouble) =>
//          throwableMonadic[Task] {
//            jumpTask().each
//            val outputData = data0 + data1
//            val computeBackward = { outputDelta: ND4JArray =>
//              val delta0Future = Do.now(outputDelta)
//              val delta1Future = throwableMonadic[Do] {
//                Do.jump().each
//                outputDelta.sumT
//              }
//              (delta0Future, delta1Future)
//            }
//            (outputData, computeBackward)
//          }
//        }
//      }
//    }
//
//    @inline
//    implicit def `Double+INDArray`(implicit logger: Logger = Logger.getGlobal,
//                                   fullName: sourcecode.FullName,
//                                   caller: Caller[_],
//                                   methodName: sourcecode.Name,
//                                   executionContext: ExecutionContext)
//      : polyFunctions.+.Case.Aux[Do[DoubleTape], Do[Tape[ND4JArray, ND4JArray]], Do[Tape[ND4JArray, ND4JArray]]] = {
//      polyFunctions.+.at { (operand0, operand1) =>
//        operand1 + operand0
//      }
//    }
//
//    @inline
//    implicit def `INDArray-INDArray`(
//        implicit logger: Logger = Logger.getGlobal,
//        fullName: sourcecode.FullName,
//        caller: Caller[_],
//        methodName: sourcecode.Name,
//        executionContext: ExecutionContext): polyFunctions.-.Case.Aux[Do[Tape[ND4JArray, ND4JArray]],
//                                                                      Do[Tape[ND4JArray, ND4JArray]],
//                                                                      Do[Tape[ND4JArray, ND4JArray]]] = {
//      polyFunctions.-.at { (operand0, operand1) =>
//        tapefactories.Binary.doTape(operand0, operand1) { (data0: ND4JArray, data1: ND4JArray) =>
//          throwableMonadic[Task] {
//            jumpTask().each
//            val outputData = {
//              val newShape = autoBroadcastShape(data0.shape(), data1.shape())
//              data0.broadcast(newShape: _*) - data1.broadcast(newShape: _*)
//            }
//            val computeBackward = { outputDelta: ND4JArray =>
//              val delta0Future = throwableMonadic[Do] {
//                Do.jump().each
//                sumAs(outputDelta, data0.shape())
//              }
//              val delta1Future = throwableMonadic[Do] {
//                Do.jump().each
//                sumAs(-outputDelta, data1.shape())
//              }
//              (delta0Future, delta1Future)
//            }
//            (outputData, computeBackward)
//          }
//        }
//      }
//    }
//
//    @inline
//    implicit def `INDArray-Double`(implicit logger: Logger = Logger.getGlobal,
//                                   fullName: sourcecode.FullName,
//                                   caller: Caller[_],
//                                   methodName: sourcecode.Name,
//                                   executionContext: ExecutionContext)
//      : polyFunctions.-.Case.Aux[Do[Tape[ND4JArray, ND4JArray]], Do[DoubleTape], Do[Tape[ND4JArray, ND4JArray]]] = {
//      polyFunctions.-.at { (operand0, operand1) =>
//        tapefactories.Binary.doTape(operand0, operand1) { (data0: ND4JArray, data1: ScalaDouble) =>
//          throwableMonadic[Task] {
//            jumpTask().each
//            val outputData = data0 - data1
//            val computeBackward = { outputDelta: ND4JArray =>
//              val delta0Future = Do.now(outputDelta)
//              val delta1Future = throwableMonadic[Do] {
//                Do.jump().each
//                -outputDelta.sumT
//              }
//              (delta0Future, delta1Future)
//            }
//            (outputData, computeBackward)
//          }
//        }
//      }
//    }
//
//    @inline
//    implicit def `Double-INDArray`(implicit logger: Logger = Logger.getGlobal,
//                                   fullName: sourcecode.FullName,
//                                   caller: Caller[_],
//                                   methodName: sourcecode.Name,
//                                   executionContext: ExecutionContext)
//      : polyFunctions.-.Case.Aux[Do[DoubleTape], Do[Tape[ND4JArray, ND4JArray]], Do[Tape[ND4JArray, ND4JArray]]] = {
//      polyFunctions.-.at { (operand0, operand1) =>
//        import shapeless.syntax.singleton._
//        -new DifferentiableINDArrayOps(operand1.narrow) + operand0
//      }
//    }
//
//    @inline
//    implicit def `INDArray*INDArray`(
//        implicit logger: Logger = Logger.getGlobal,
//        fullName: sourcecode.FullName,
//        caller: Caller[_],
//        methodName: sourcecode.Name,
//        executionContext: ExecutionContext): polyFunctions.*.Case.Aux[Do[Tape[ND4JArray, ND4JArray]],
//                                                                      Do[Tape[ND4JArray, ND4JArray]],
//                                                                      Do[Tape[ND4JArray, ND4JArray]]] = {
//      polyFunctions.*.at { (operand0, operand1) =>
//        tapefactories.Binary.doTape(operand0, operand1) { (data0: ND4JArray, data1: ND4JArray) =>
//          throwableMonadic[Task] {
//            jumpTask().each
//            val outputData = {
//              val newShape = autoBroadcastShape(data0.shape(), data1.shape())
//              data0.broadcast(newShape: _*) * data1.broadcast(newShape: _*)
//            }
//            val computeBackward = { outputDelta: ND4JArray =>
//              val delta0Future = throwableMonadic[Do] {
//                Do.jump().each
//                sumAs(data1.broadcast(outputDelta.shape(): _*) * outputDelta, data0.shape())
//              }
//              val delta1Future = throwableMonadic[Do] {
//                Do.jump().each
//                sumAs(data0.broadcast(outputDelta.shape(): _*) * outputDelta, data1.shape())
//              }
//              (delta0Future, delta1Future)
//            }
//            (outputData, computeBackward)
//          }
//        }
//      }
//    }
//
//    @inline
//    implicit def `INDArray*Double`(implicit logger: Logger = Logger.getGlobal,
//                                   fullName: sourcecode.FullName,
//                                   caller: Caller[_],
//                                   methodName: sourcecode.Name,
//                                   executionContext: ExecutionContext)
//      : polyFunctions.*.Case.Aux[Do[Tape[ND4JArray, ND4JArray]], Do[DoubleTape], Do[Tape[ND4JArray, ND4JArray]]] = {
//      polyFunctions.*.at { (operand0, operand1) =>
//        tapefactories.Binary.doTape(operand0, operand1) { (data0: ND4JArray, data1: ScalaDouble) =>
//          throwableMonadic[Task] {
//            jumpTask().each
//            val outputData = data0 * data1
//            val computeBackward = { outputDelta: ND4JArray =>
//              val delta0Future = throwableMonadic[Do] {
//                Do.jump().each
//                outputDelta * data1
//              }
//              val delta1Future = throwableMonadic[Do] {
//                (data0 * outputDelta).sumT
//              }
//              (delta0Future, delta1Future)
//            }
//            (outputData, computeBackward)
//          }
//        }
//      }
//    }
//
//    @inline
//    implicit def `Double*INDArray`(implicit logger: Logger = Logger.getGlobal,
//                                   fullName: sourcecode.FullName,
//                                   caller: Caller[_],
//                                   methodName: sourcecode.Name,
//                                   executionContext: ExecutionContext)
//      : polyFunctions.*.Case.Aux[Do[DoubleTape], Do[Tape[ND4JArray, ND4JArray]], Do[Tape[ND4JArray, ND4JArray]]] = {
//      polyFunctions.*.at { (operand0, operand1) =>
//        operand1 * operand0
//      }
//    }
//
//    @inline
//    implicit def `INDArray/INDArray`(
//        implicit logger: Logger = Logger.getGlobal,
//        fullName: sourcecode.FullName,
//        caller: Caller[_],
//        methodName: sourcecode.Name,
//        executionContext: ExecutionContext): polyFunctions./.Case.Aux[Do[Tape[ND4JArray, ND4JArray]],
//                                                                      Do[Tape[ND4JArray, ND4JArray]],
//                                                                      Do[Tape[ND4JArray, ND4JArray]]] = {
//      polyFunctions./.at { (operand0, operand1) =>
//        operand0 * reciprocal(operand1)
//      }
//    }
//
//    @inline
//    implicit def `INDArray/Double`(implicit logger: Logger = Logger.getGlobal,
//                                   fullName: sourcecode.FullName,
//                                   caller: Caller[_],
//                                   methodName: sourcecode.Name,
//                                   executionContext: ExecutionContext)
//      : polyFunctions./.Case.Aux[Do[Tape[ND4JArray, ND4JArray]], Do[DoubleTape], Do[Tape[ND4JArray, ND4JArray]]] = {
//      polyFunctions./.at { (operand0, operand1) =>
//        operand0 * Double.implicits.reciprocal(operand1)
//      }
//    }
//
//    @inline
//    implicit def `Double/INDArray`(implicit logger: Logger = Logger.getGlobal,
//                                   fullName: sourcecode.FullName,
//                                   caller: Caller[_],
//                                   methodName: sourcecode.Name,
//                                   executionContext: ExecutionContext)
//      : polyFunctions./.Case.Aux[Do[DoubleTape], Do[Tape[ND4JArray, ND4JArray]], Do[Tape[ND4JArray, ND4JArray]]] = {
//      polyFunctions./.at { (operand0, operand1) =>
//        operand0 * reciprocal(operand1)
//      }
//    }
//
//    @inline
//    implicit def `max(INDArray,Double)`(implicit logger: Logger = Logger.getGlobal,
//                                        fullName: sourcecode.FullName,
//                                        caller: Caller[_],
//                                        methodName: sourcecode.Name,
//                                        executionContext: ExecutionContext)
//      : polyFunctions.max.Case.Aux[Do[Tape[ND4JArray, ND4JArray]], Do[DoubleTape], Do[Tape[ND4JArray, ND4JArray]]] = {
//      polyFunctions.max.at { (operand0, operand1) =>
//        tapefactories.Binary.doTape(operand0, operand1) { (data0: ND4JArray, data1: ScalaDouble) =>
//          throwableMonadic[Task] {
//            jumpTask().each
//            val outputData = Transforms.max(data0, data1)
//            val computeBackward = { outputDelta: ND4JArray =>
//              val delta0Future = throwableMonadic[Do] {
//                Do.jump().each
//                (data0 gt data1) * outputDelta
//              }
//              val delta1Future = throwableMonadic[Do] {
//                Do.jump().each
//                ((data0 lt data1) * outputDelta).sumT
//              }
//              (delta0Future, delta1Future)
//            }
//            (outputData, computeBackward)
//          }
//        }
//      }
//    }
//
//    @inline
//    implicit def `min(INDArray,Double)`(implicit logger: Logger = Logger.getGlobal,
//                                        fullName: sourcecode.FullName,
//                                        caller: Caller[_],
//                                        methodName: sourcecode.Name,
//                                        executionContext: ExecutionContext)
//      : polyFunctions.min.Case.Aux[Do[Tape[ND4JArray, ND4JArray]], Do[DoubleTape], Do[Tape[ND4JArray, ND4JArray]]] = {
//      polyFunctions.min.at { (operand0, operand1) =>
//        tapefactories.Binary.doTape(operand0, operand1) { (data0: ND4JArray, data1: ScalaDouble) =>
//          throwableMonadic[Task] {
//            jumpTask().each
//            val outputData = Transforms.min(data0, data1)
//            val computeBackward = { outputDelta: ND4JArray =>
//              val delta0Future = throwableMonadic[Do] {
//                Do.jump().each
//                (data0 lt data1) * outputDelta
//              }
//              val delta1Future = throwableMonadic[Do] {
//                Do.jump().each
//                ((data0 gt data1) * outputDelta).sumT
//              }
//              (delta0Future, delta1Future)
//            }
//            (outputData, computeBackward)
//          }
//        }
//      }
//    }
//
//    @inline
//    implicit def `exp(INDArray)`(implicit logger: Logger = Logger.getGlobal,
//                                 fullName: sourcecode.FullName,
//                                 caller: Caller[_],
//                                 methodName: sourcecode.Name,
//                                 executionContext: ExecutionContext)
//      : polyFunctions.exp.Case.Aux[Do[Tape[ND4JArray, ND4JArray]], Do[Tape[ND4JArray, ND4JArray]]] = {
//      polyFunctions.exp.at { operand =>
//        tapefactories.Unary.doTape(operand) { (data: ND4JArray) =>
//          throwableMonadic[Task] {
//            jumpTask().each
//            val outputData = Transforms.exp(data)
//            val computeBackward = { outputDelta: ND4JArray =>
//              throwableMonadic[Do] {
//                Do.jump().each
//                outputData * outputDelta
//              }
//            }
//            (outputData, computeBackward)
//          }
//        }
//      }
//    }
//
//    @inline
//    implicit def `log(INDArray)`(implicit logger: Logger = Logger.getGlobal,
//                                 fullName: sourcecode.FullName,
//                                 caller: Caller[_],
//                                 methodName: sourcecode.Name,
//                                 executionContext: ExecutionContext)
//      : polyFunctions.log.Case.Aux[Do[Tape[ND4JArray, ND4JArray]], Do[Tape[ND4JArray, ND4JArray]]] = {
//      polyFunctions.log.at { operand =>
//        tapefactories.Unary.doTape(operand) { (data: ND4JArray) =>
//          throwableMonadic[Task] {
//            jumpTask().each
//            val outputData = Transforms.log(data)
//            val computeBackward = { outputDelta: ND4JArray =>
//              throwableMonadic[Do] {
//                Do.jump().each
//                outputDelta / data
//              }
//            }
//            (outputData, computeBackward)
//          }
//        }
//      }
//    }
//
//    @inline
//    implicit def `abs(INDArray)`(implicit logger: Logger = Logger.getGlobal,
//                                 fullName: sourcecode.FullName,
//                                 caller: Caller[_],
//                                 methodName: sourcecode.Name,
//                                 executionContext: ExecutionContext)
//      : polyFunctions.abs.Case.Aux[Do[Tape[ND4JArray, ND4JArray]], Do[Tape[ND4JArray, ND4JArray]]] = {
//      polyFunctions.abs.at { operand =>
//        tapefactories.Unary.doTape(operand) { (data: ND4JArray) =>
//          throwableMonadic[Task] {
//            jumpTask().each
//            val outputData = Transforms.abs(data)
//            val computeBackward = { outputDelta: ND4JArray =>
//              throwableMonadic[Do] {
//                Do.jump().each
//                outputDelta * Transforms.sign(data)
//              }
//            }
//            (outputData, computeBackward)
//          }
//        }
//      }
//    }
//
//    @inline
//    def negative[Operand](operand: Operand)(implicit liftOperand: Lift.Aux[Operand, ND4JArray, ND4JArray],
//                                            logger: Logger = Logger.getGlobal,
//                                            fullName: sourcecode.FullName,
//                                            caller: Caller[_],
//                                            methodName: sourcecode.Name,
//                                            executionContext: ExecutionContext): Do[Tape[ND4JArray, ND4JArray]] = {
//      tapefactories.Unary.doTape(liftOperand(operand)) { data =>
//        throwableMonadic[Task] {
//          jumpTask().each
//          val outputData = -data
//          val computeBackward = { outputDelta: ND4JArray =>
//            throwableMonadic[Do] {
//              Do.jump().each
//              -outputDelta
//            }
//          }
//          (outputData, computeBackward)
//        }
//      }
//    }
//
//    @inline
//    def reciprocal[Operand](operand: Operand)(implicit liftOperand: Lift.Aux[Operand, ND4JArray, ND4JArray],
//                                              logger: Logger = Logger.getGlobal,
//                                              fullName: sourcecode.FullName,
//                                              caller: Caller[_],
//                                              methodName: sourcecode.Name,
//                                              executionContext: ExecutionContext): Do[Tape[ND4JArray, ND4JArray]] = {
//      tapefactories.Unary.doTape(liftOperand(operand)) { data: ND4JArray =>
//        throwableMonadic[Task] {
//          val outputData = data rdiv 1.0
//          val computeBackward = { outputDelta: ND4JArray =>
//            throwableMonadic[Do] {
//              Do.jump().each
//              -outputDelta / (data * data)
//            }
//          }
//          (outputData, computeBackward)
//        }
//      }
//    }
//
//    @inline
//    def conv2d[Input, Weight, Bias](input: Input,
//                                    weight: Weight,
//                                    bias: Bias,
//                                    kernel: (Int, Int),
//                                    stride: (Int, Int),
//                                    padding: (Int, Int))(
//        implicit liftInput: Lift.Aux[Input, ND4JArray, ND4JArray],
//        liftWeight: Lift.Aux[Weight, ND4JArray, ND4JArray],
//        liftBias: Lift.Aux[Bias, ND4JArray, ND4JArray],
//        logger: Logger = Logger.getGlobal,
//        fullName: sourcecode.FullName,
//        caller: Caller[_],
//        methodName: sourcecode.Name,
//        executionContext: ExecutionContext): Do[Tape[ND4JArray, ND4JArray]] = {
//      def monadicConv2d[InputTape <: Tape[ND4JArray, ND4JArray],
//                        WeightTape <: Tape[ND4JArray, ND4JArray],
//                        Bias <: Tape[ND4JArray, ND4JArray]](input: Do[InputTape],
//                                                            weight: Do[WeightTape],
//                                                            bias: Do[Bias]) = monadic[Do] {
//        val inputShape: Array[Int] = input.each.data.shape()
//        val count = inputShape(0)
//        val depth = inputShape(1)
//        val yAxis = inputShape(2)
//        val xAxis = inputShape(3)
//
//        val weightShape: Array[Int] = weight.each.data.shape()
//
//        val kernelNumber = weightShape(0)
//        val col = im2col(input, kernel, stride, padding)
//        val permutedCol: Do[Tape[ND4JArray, ND4JArray]] = permute(col, 0, 4, 5, 1, 2, 3)
//        val depthKernelKernel = depth * kernel._1 * kernel._2
//        val countXAisYAis = count * yAxis * xAxis
//
//        val operandCol2d = reshape(permutedCol, countXAisYAis, depthKernelKernel)
//
//        val reshapedWeight = reshape(weight, kernelNumber, depthKernelKernel)
//
//        val permutedWeight = permute(reshapedWeight, 1, 0)
//
//        val dotResult: Do[Tape[ND4JArray, ND4JArray]] = dot(operandCol2d, permutedWeight)
//
//        val plusResult: Do[Tape[ND4JArray, ND4JArray]] = dotResult + bias
//
//        val reshapeResult = reshape(plusResult, count, yAxis, xAxis, kernelNumber)
//
//        permute(reshapeResult, 0, 3, 1, 2).each
//
//      }
//
//      monadicConv2d(
//        liftInput(input): Do[Tape[ND4JArray, ND4JArray]],
//        liftWeight(weight): Do[Tape[ND4JArray, ND4JArray]],
//        liftBias(bias): Do[Tape[ND4JArray, ND4JArray]]
//      )
//    }
//
//    @inline
//    def dot[Left, Right](left: Left, right: Right)(
//        implicit liftLeft: Lift.Aux[Left, ND4JArray, ND4JArray],
//        liftRight: Lift.Aux[Right, ND4JArray, ND4JArray],
//        logger: Logger = Logger.getGlobal,
//        fullName: sourcecode.FullName,
//        caller: Caller[_],
//        methodName: sourcecode.Name,
//        executionContext: ExecutionContext): Do[Tape[ND4JArray, ND4JArray]] = {
//      tapefactories.Binary.doTape(liftLeft(left), liftRight(right)) { (data0: ND4JArray, data1: ND4JArray) =>
//        throwableMonadic[Task] {
//          jumpTask().each
//
//          val outputData = data0 dot data1
//
//          val computeBackward = { outputDelta: ND4JArray =>
//            val delta0Future =
//              throwableMonadic[Do] {
//                Do.jump().each
//                outputDelta dot data1.T
//              }
//
//            val delta1Future =
//              throwableMonadic[Do] {
//                Do.jump().each
//                data0.T dot outputDelta
//              }
//            (delta0Future, delta1Future)
//          }
//          (outputData, computeBackward)
//        }
//      }
//    }
//
//    private def toArray(tuple2: (Int, Int)): Array[Int] = {
//      val (one, two) = tuple2
//      Array(one, two)
//    }
//
//    @inline
//    def im2col[Operand](operand: Operand, kernel: (Int, Int), stride: (Int, Int), padding: (Int, Int))(
//        implicit liftOperand: Lift.Aux[Operand, ND4JArray, ND4JArray],
//        logger: Logger = Logger.getGlobal,
//        fullName: sourcecode.FullName,
//        caller: Caller[_],
//        methodName: sourcecode.Name,
//        executionContext: ExecutionContext): Do[Tape[ND4JArray, ND4JArray]] = {
//      tapefactories.Unary.doTape(liftOperand(operand)) { data: ND4JArray =>
//        throwableMonadic[Task] {
//          jumpTask().each
//          val dataShape = data.shape()
//          val strideArray = toArray(stride)
//          val paddingArray = toArray(padding)
//          val outputData = Convolution.im2col(data, toArray(kernel), strideArray, paddingArray)
//          val computeBackward = { outputDelta: ND4JArray =>
//            throwableMonadic[Do] {
//              Do.jump().each
//              Convolution.col2im(outputDelta, strideArray, paddingArray, dataShape(2), dataShape(3))
//            }
//          }
//          (outputData, computeBackward)
//        }
//      }
//    }
//
//    @inline
//    def reshape[Operand](operand: Operand, newShape: Int*)(
//        implicit liftOperand: Lift.Aux[Operand, ND4JArray, ND4JArray],
//        logger: Logger = Logger.getGlobal,
//        fullName: sourcecode.FullName,
//        caller: Caller[_],
//        methodName: sourcecode.Name,
//        executionContext: ExecutionContext): Do[Tape[ND4JArray, ND4JArray]] = {
//      tapefactories.Unary.doTape(liftOperand(operand)) { (data: ND4JArray) =>
//        throwableMonadic[Task] {
//          jumpTask().each
//          val dataShape = data.shape()
//          val outputData = data.reshape(newShape: _*)
//          val computeBackward = { outputDelta: ND4JArray =>
//            throwableMonadic[Do] {
//              Do.jump().each
//              outputDelta.reshape(dataShape: _*)
//            }
//          }
//          (outputData, computeBackward)
//        }
//      }
//    }
//
//    @inline
//    def permute[Operand](operand: Operand, dimensions: Int*)(
//        implicit liftOperand: Lift.Aux[Operand, ND4JArray, ND4JArray],
//        logger: Logger = Logger.getGlobal,
//        fullName: sourcecode.FullName,
//        caller: Caller[_],
//        methodName: sourcecode.Name,
//        executionContext: ExecutionContext): Do[Tape[ND4JArray, ND4JArray]] = {
//      tapefactories.Unary.doTape(liftOperand(operand)) { (data: ND4JArray) =>
//        throwableMonadic[Task] {
//          jumpTask().each
//          val dataShape = data.shape()
//          val outputData = data.permute(dimensions: _*)
//          val computeBackward = { outputDelta: ND4JArray =>
//            throwableMonadic[Do] {
//              Do.jump().each
//              val indexedSeq: IndexedSeq[Int] = dataShape.indices
//                .map { index =>
//                  dimensions.indexOf(index)
//                }
//              outputDelta.permute(indexedSeq: _*)
//            }
//          }
//          (outputData, computeBackward)
//        }
//      }
//    }
//
//    @inline
//    def sumT[Operand](operand: Operand)(implicit liftOperand: Lift.Aux[Operand, ND4JArray, ND4JArray],
//                                        logger: Logger = Logger.getGlobal,
//                                        fullName: sourcecode.FullName,
//                                        caller: Caller[_],
//                                        methodName: sourcecode.Name,
//                                        executionContext: ExecutionContext): Do[DoubleTape] = {
//      tapefactories.Unary.doTape(liftOperand(operand)) { data: ND4JArray =>
//        throwableMonadic[Task] {
//          jumpTask().each
//          val outputData = data.sumT
//          val computeBackward = { outputDelta: ScalaDouble =>
//            throwableMonadic[Do] {
//              Do.jump().each
//              Nd4j.valueArrayOf(data.shape(), outputDelta)
//            }
//          }
//          (outputData, computeBackward)
//        }
//      }
//    }
//
//    @inline
//    def sum[Operand](operand: Operand, dimensions: Int*)(
//        implicit liftOperand: Lift.Aux[Operand, ND4JArray, ND4JArray],
//        logger: Logger = Logger.getGlobal,
//        fullName: sourcecode.FullName,
//        caller: Caller[_],
//        methodName: sourcecode.Name,
//        executionContext: ExecutionContext): Do[Tape[ND4JArray, ND4JArray]] = {
//      tapefactories.Unary.doTape(liftOperand(operand)) { data: ND4JArray =>
//        throwableMonadic[Task] {
//          jumpTask().each
//          val outputData = data.sum(dimensions: _*)
//          val computeBackward = { outputDelta: ND4JArray =>
//            throwableMonadic[Do] {
//              Do.jump().each
//              outputDelta.broadcast(data.shape(): _*)
//            }
//          }
//          (outputData, computeBackward)
//        }
//      }
//    }
//
//    @inline
//    def mean[Operand](operand: Operand)(implicit liftOperand: Lift.Aux[Operand, ND4JArray, ND4JArray],
//                                        logger: Logger = Logger.getGlobal,
//                                        fullName: sourcecode.FullName,
//                                        caller: Caller[_],
//                                        methodName: sourcecode.Name,
//                                        executionContext: ExecutionContext): Do[DoubleTape] = {
//      tapefactories.Unary.doTape(liftOperand(operand)) { data: ND4JArray =>
//        throwableMonadic[Task] {
//          jumpTask().each
//          val outputData = data.sumT / ArrayUtil.prod(data.shape(): _*)
//          val computeBackward = { outputDelta: ScalaDouble =>
//            throwableMonadic[Do] {
//              Do.jump().each
//              Nd4j.valueArrayOf(data.shape(), outputDelta)
//            }
//          }
//          (outputData, computeBackward)
//        }
//      }
//    }
//
//    implicit final class DifferentiableINDArrayOps[Operand](operand: Operand)(
//        implicit liftOperand: Lift.Aux[Operand, ND4JArray, ND4JArray],
//        logger: Logger = Logger.getGlobal,
//        fullName: sourcecode.FullName,
//        methodName: sourcecode.Name,
//        caller: Caller[_],
//        executionContext: ExecutionContext) {
//      @inline
//      def unary_- : Do[Tape[ND4JArray, ND4JArray]] = {
//        tapefactories.Unary.doTape(liftOperand(operand)) { data =>
//          Task.delay {
//            val outputData = -data
//            val computeBackward = { outputDelta: ND4JArray =>
//              throwableMonadic[Do] {
//                Do.jump().each
//                -outputDelta
//              }
//            }
//            (outputData, computeBackward)
//          }
//        }
//      }
//    }
//  }
//}
