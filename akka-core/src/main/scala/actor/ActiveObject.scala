/**
 * Copyright (C) 2009-2010 Scalable Solutions AB <http://scalablesolutions.se>
 */

package se.scalablesolutions.akka.actor

import se.scalablesolutions.akka.config.FaultHandlingStrategy
import se.scalablesolutions.akka.remote.protobuf.RemoteProtocol.RemoteRequestProtocol
import se.scalablesolutions.akka.remote.{RemoteProtocolBuilder, RemoteClient, RemoteRequestProtocolIdFactory}
import se.scalablesolutions.akka.dispatch.{MessageDispatcher, Future}
import se.scalablesolutions.akka.config.ScalaConfig._
import se.scalablesolutions.akka.serialization.Serializer
import se.scalablesolutions.akka.util._

import org.codehaus.aspectwerkz.joinpoint.{MethodRtti, JoinPoint}
import org.codehaus.aspectwerkz.proxy.Proxy
import org.codehaus.aspectwerkz.annotation.{Aspect, Around}

import java.net.InetSocketAddress
import java.lang.reflect.{InvocationTargetException, Method}

object Annotations {
  import se.scalablesolutions.akka.actor.annotation._
  val oneway =                 classOf[oneway]
  val transactionrequired =    classOf[transactionrequired]
  val prerestart =             classOf[prerestart]
  val postrestart =            classOf[postrestart]
  val immutable =              classOf[immutable]
  val inittransactionalstate = classOf[inittransactionalstate]
}

/**
 * Configuration factory for Active Objects. 
 * 
 * FIXDOC: document ActiveObjectConfiguration
 */
final class ActiveObjectConfiguration {
  private[akka] var _timeout: Long = Actor.TIMEOUT
  private[akka] var _restartCallbacks: Option[RestartCallbacks] = None
  private[akka] var _transactionRequired = false
  private[akka] var _host: Option[InetSocketAddress] = None
  private[akka] var _messageDispatcher: Option[MessageDispatcher] = None

  def timeout(timeout: Long) : ActiveObjectConfiguration = {
    _timeout = timeout
    this
  }

  def restartCallbacks(pre: String, post: String) : ActiveObjectConfiguration = {
    _restartCallbacks = Some(new RestartCallbacks(pre, post))
    this
  }

  def makeTransactionRequired() : ActiveObjectConfiguration = {
    _transactionRequired = true;
    this
  }

  def makeRemote(hostname: String, port: Int) : ActiveObjectConfiguration = {
    _host = Some(new InetSocketAddress(hostname, port))
    this
  }

  def dispatcher(messageDispatcher: MessageDispatcher) : ActiveObjectConfiguration = {
    _messageDispatcher = Some(messageDispatcher)
    this
  }
}

/**
 * Factory class for creating Active Objects out of plain POJOs and/or POJOs with interfaces.
 *
 * @author <a href="http://jonasboner.com">Jonas Bon&#233;r</a>
 */
object ActiveObject {
  import Actor.actorOf
  
  val AKKA_CAMEL_ROUTING_SCHEME = "akka"
  private[actor] val AW_PROXY_PREFIX = "$$ProxiedByAW".intern

  def newInstance[T](target: Class[T], timeout: Long): T =
    newInstance(target, actorOf(new Dispatcher(false, None)), None, timeout)

  def newInstance[T](target: Class[T]): T =
    newInstance(target, actorOf(new Dispatcher(false, None)), None, Actor.TIMEOUT)

  def newInstance[T](intf: Class[T], target: AnyRef, timeout: Long): T =
    newInstance(intf, target, actorOf(new Dispatcher(false, None)), None, timeout)

  def newInstance[T](intf: Class[T], target: AnyRef): T =
    newInstance(intf, target, actorOf(new Dispatcher(false, None)), None, Actor.TIMEOUT)

  def newRemoteInstance[T](target: Class[T], timeout: Long, hostname: String, port: Int): T =
    newInstance(target, actorOf(new Dispatcher(false, None)), Some(new InetSocketAddress(hostname, port)), timeout)

  def newRemoteInstance[T](target: Class[T], hostname: String, port: Int): T =
    newInstance(target, actorOf(new Dispatcher(false, None)), Some(new InetSocketAddress(hostname, port)), Actor.TIMEOUT)

  def newInstance[T](target: Class[T], config: ActiveObjectConfiguration): T = {
    val actor = actorOf(new Dispatcher(config._transactionRequired, config._restartCallbacks))
     if (config._messageDispatcher.isDefined) {
       actor.dispatcher = config._messageDispatcher.get
     }
     newInstance(target, actor, config._host, config._timeout)
  }

  def newInstance[T](intf: Class[T], target: AnyRef, config: ActiveObjectConfiguration): T = {
    val actor = actorOf(new Dispatcher(config._transactionRequired, config._restartCallbacks))
     if (config._messageDispatcher.isDefined) {
       actor.dispatcher = config._messageDispatcher.get
     }
     newInstance(intf, target, actor, config._host, config._timeout)
  }

  @deprecated("use newInstance(target: Class[T], config: ActiveObjectConfiguration) instead")
  def newInstance[T](target: Class[T], timeout: Long, restartCallbacks: Option[RestartCallbacks]): T =
    newInstance(target, actorOf(new Dispatcher(false, restartCallbacks)), None, timeout)

  @deprecated("use newInstance(intf: Class[T], target: AnyRef, config: ActiveObjectConfiguration) instead")
  def newInstance[T](intf: Class[T], target: AnyRef, timeout: Long, restartCallbacks: Option[RestartCallbacks]): T =
    newInstance(intf, target, actorOf(new Dispatcher(false, restartCallbacks)), None, timeout)

  @deprecated("use newInstance(target: Class[T], config: ActiveObjectConfiguration) instead")
  def newInstance[T](target: Class[T], timeout: Long, transactionRequired: Boolean): T =
    newInstance(target, actorOf(new Dispatcher(transactionRequired, None)), None, timeout)

  @deprecated("use newInstance(target: Class[T], config: ActiveObjectConfiguration) instead")
  def newInstance[T](target: Class[T], timeout: Long, transactionRequired: Boolean, restartCallbacks: Option[RestartCallbacks]): T =
    newInstance(target, actorOf(new Dispatcher(transactionRequired, restartCallbacks)), None, timeout)

  @deprecated("use newInstance(intf: Class[T], target: AnyRef, config: ActiveObjectConfiguration) instead")
  def newInstance[T](intf: Class[T], target: AnyRef, timeout: Long, transactionRequired: Boolean): T =
    newInstance(intf, target, actorOf(new Dispatcher(transactionRequired, None)), None, timeout)

  @deprecated("use newInstance(intf: Class[T], target: AnyRef, config: ActiveObjectConfiguration) instead")
  def newInstance[T](intf: Class[T], target: AnyRef, timeout: Long, transactionRequired: Boolean, restartCallbacks: Option[RestartCallbacks]): T =
    newInstance(intf, target, actorOf(new Dispatcher(transactionRequired, restartCallbacks)), None, timeout)

  @deprecated("use newInstance(intf: Class[T], target: AnyRef, config: ActiveObjectConfiguration) instead")
  def newRemoteInstance[T](intf: Class[T], target: AnyRef, timeout: Long, hostname: String, port: Int): T =
    newInstance(intf, target, actorOf(new Dispatcher(false, None)), Some(new InetSocketAddress(hostname, port)), timeout)

  @deprecated("use newInstance(intf: Class[T], target: AnyRef, config: ActiveObjectConfiguration) instead")
  def newRemoteInstance[T](intf: Class[T], target: AnyRef, timeout: Long, hostname: String, port: Int, restartCallbacks: Option[RestartCallbacks]): T =
    newInstance(intf, target, actorOf(new Dispatcher(false, restartCallbacks)), Some(new InetSocketAddress(hostname, port)), timeout)

  @deprecated("use newInstance(target: Class[T], config: ActiveObjectConfiguration) instead")
  def newRemoteInstance[T](target: Class[T], timeout: Long, transactionRequired: Boolean, hostname: String, port: Int): T =
    newInstance(target, actorOf(new Dispatcher(transactionRequired, None)), Some(new InetSocketAddress(hostname, port)), timeout)

  @deprecated("use newInstance(target: Class[T], config: ActiveObjectConfiguration) instead")
  def newRemoteInstance[T](target: Class[T], timeout: Long, transactionRequired: Boolean, hostname: String, port: Int, restartCallbacks: Option[RestartCallbacks]): T =
    newInstance(target, actorOf(new Dispatcher(transactionRequired, restartCallbacks)), Some(new InetSocketAddress(hostname, port)), timeout)

  @deprecated("use newInstance(intf: Class[T], target: AnyRef, config: ActiveObjectConfiguration) instead")
  def newRemoteInstance[T](intf: Class[T], target: AnyRef, timeout: Long, transactionRequired: Boolean, hostname: String, port: Int): T =
    newInstance(intf, target, actorOf(new Dispatcher(transactionRequired, None)), Some(new InetSocketAddress(hostname, port)), timeout)

  @deprecated("use newInstance(intf: Class[T], target: AnyRef, config: ActiveObjectConfiguration) instead")
  def newRemoteInstance[T](intf: Class[T], target: AnyRef, timeout: Long, transactionRequired: Boolean, hostname: String, port: Int, restartCallbacks: Option[RestartCallbacks]): T =
    newInstance(intf, target, actorOf(new Dispatcher(transactionRequired, restartCallbacks)), Some(new InetSocketAddress(hostname, port)), timeout)

  @deprecated("use newInstance(target: Class[T], config: ActiveObjectConfiguration) instead")
  def newInstance[T](target: Class[T], timeout: Long, dispatcher: MessageDispatcher): T = {
    val actor = actorOf(new Dispatcher(false, None))
    actor.dispatcher = dispatcher
    newInstance(target, actor, None, timeout)
  }

  @deprecated("use newInstance(target: Class[T], config: ActiveObjectConfiguration) instead")
  def newInstance[T](target: Class[T], timeout: Long, dispatcher: MessageDispatcher, restartCallbacks: Option[RestartCallbacks]): T = {
    val actor = actorOf(new Dispatcher(false, restartCallbacks))
    actor.dispatcher = dispatcher
    newInstance(target, actor, None, timeout)
  }

  @deprecated("use newInstance(intf: Class[T], target: AnyRef, config: ActiveObjectConfiguration) instead")
  def newInstance[T](intf: Class[T], target: AnyRef, timeout: Long, dispatcher: MessageDispatcher): T = {
    val actor = actorOf(new Dispatcher(false, None))
    actor.dispatcher = dispatcher
    newInstance(intf, target, actor, None, timeout)
  }

  @deprecated("use newInstance(intf: Class[T], target: AnyRef, config: ActiveObjectConfiguration) instead")
  def newInstance[T](intf: Class[T], target: AnyRef, timeout: Long, 
                     dispatcher: MessageDispatcher, restartCallbacks: Option[RestartCallbacks]): T = {
    val actor = actorOf(new Dispatcher(false, restartCallbacks))
    actor.dispatcher = dispatcher
    newInstance(intf, target, actor, None, timeout)
  }

  @deprecated("use newInstance(target: Class[T], config: ActiveObjectConfiguration) instead")
  def newInstance[T](target: Class[T], timeout: Long, transactionRequired: Boolean, dispatcher: MessageDispatcher): T = {
    val actor = actorOf(new Dispatcher(transactionRequired, None))
    actor.dispatcher = dispatcher
    newInstance(target, actor, None, timeout)
  }

  @deprecated("use newInstance(target: Class[T], config: ActiveObjectConfiguration) instead")
  def newInstance[T](target: Class[T], timeout: Long, transactionRequired: Boolean, 
                     dispatcher: MessageDispatcher, restartCallbacks: Option[RestartCallbacks]): T = {
    val actor = actorOf(new Dispatcher(transactionRequired, restartCallbacks))
    actor.dispatcher = dispatcher
    newInstance(target, actor, None, timeout)
  }

  @deprecated("use newInstance(intf: Class[T], target: AnyRef, config: ActiveObjectConfiguration) instead")
  def newInstance[T](intf: Class[T], target: AnyRef, timeout: Long, transactionRequired: Boolean, dispatcher: MessageDispatcher): T = {
    val actor = actorOf(new Dispatcher(transactionRequired, None))
    actor.dispatcher = dispatcher
    newInstance(intf, target, actor, None, timeout)
  }

  @deprecated("use newInstance(intf: Class[T], target: AnyRef, config: ActiveObjectConfiguration) instead")
  def newInstance[T](intf: Class[T], target: AnyRef, timeout: Long, transactionRequired: Boolean, 
                     dispatcher: MessageDispatcher, restartCallbacks: Option[RestartCallbacks]): T = {
    val actor = actorOf(new Dispatcher(transactionRequired, restartCallbacks))
    actor.dispatcher = dispatcher
    newInstance(intf, target, actor, None, timeout)
  }

  @deprecated("use newInstance(target: Class[T], config: ActiveObjectConfiguration) instead")
  def newRemoteInstance[T](target: Class[T], timeout: Long, dispatcher: MessageDispatcher, hostname: String, port: Int): T = {
    val actor = actorOf(new Dispatcher(false, None))
    actor.dispatcher = dispatcher
    newInstance(target, actor, Some(new InetSocketAddress(hostname, port)), timeout)
  }

  @deprecated("use newInstance(target: Class[T], config: ActiveObjectConfiguration) instead")
  def newRemoteInstance[T](target: Class[T], timeout: Long, dispatcher: MessageDispatcher, 
                           hostname: String, port: Int, restartCallbacks: Option[RestartCallbacks]): T = {
    val actor = actorOf(new Dispatcher(false, restartCallbacks))
    actor.dispatcher = dispatcher
    newInstance(target, actor, Some(new InetSocketAddress(hostname, port)), timeout)
  }

  @deprecated("use newInstance(intf: Class[T], target: AnyRef, config: ActiveObjectConfiguration) instead")
  def newRemoteInstance[T](intf: Class[T], target: AnyRef, timeout: Long, dispatcher: MessageDispatcher, hostname: String, port: Int): T = {
    val actor = actorOf(new Dispatcher(false, None))
    actor.dispatcher = dispatcher
    newInstance(intf, target, actor, Some(new InetSocketAddress(hostname, port)), timeout)
  }

  @deprecated("use newInstance(intf: Class[T], target: AnyRef, config: ActiveObjectConfiguration) instead")
  def newRemoteInstance[T](intf: Class[T], target: AnyRef, timeout: Long, dispatcher: MessageDispatcher, 
                           hostname: String, port: Int, restartCallbacks: Option[RestartCallbacks]): T = {
    val actor = actorOf(new Dispatcher(false, restartCallbacks))
    actor.dispatcher = dispatcher
    newInstance(intf, target, actor, Some(new InetSocketAddress(hostname, port)), timeout)
  }

  @deprecated("use newInstance(target: Class[T], config: ActiveObjectConfiguration) instead")
  def newRemoteInstance[T](target: Class[T], timeout: Long, transactionRequired: Boolean, 
                           dispatcher: MessageDispatcher, hostname: String, port: Int): T = {
    val actor = actorOf(new Dispatcher(transactionRequired, None))
    actor.dispatcher = dispatcher
    newInstance(target, actor, Some(new InetSocketAddress(hostname, port)), timeout)
  }

  @deprecated("use newInstance(target: Class[T], config: ActiveObjectConfiguration) instead")
  def newRemoteInstance[T](target: Class[T], timeout: Long, transactionRequired: Boolean, dispatcher: MessageDispatcher, 
                          hostname: String, port: Int, restartCallbacks: Option[RestartCallbacks]): T = {
    val actor = actorOf(new Dispatcher(transactionRequired, restartCallbacks))
    actor.dispatcher = dispatcher
    newInstance(target, actor, Some(new InetSocketAddress(hostname, port)), timeout)
  }

  @deprecated("use newInstance(intf: Class[T], target: AnyRef, config: ActiveObjectConfiguration) instead")
  def newRemoteInstance[T](intf: Class[T], target: AnyRef, timeout: Long, transactionRequired: Boolean, 
                           dispatcher: MessageDispatcher, hostname: String, port: Int): T = {
    val actor = actorOf(new Dispatcher(transactionRequired, None))
    actor.dispatcher = dispatcher
    newInstance(intf, target, actor, Some(new InetSocketAddress(hostname, port)), timeout)
  }

  @deprecated("use newInstance(intf: Class[T], target: AnyRef, config: ActiveObjectConfiguration) instead")
  def newRemoteInstance[T](intf: Class[T], target: AnyRef, timeout: Long, transactionRequired: Boolean, 
                           dispatcher: MessageDispatcher, hostname: String, port: Int, restartCallbacks: Option[RestartCallbacks]): T = {
    val actor = actorOf(new Dispatcher(transactionRequired, restartCallbacks))
    actor.dispatcher = dispatcher
    newInstance(intf, target, actor, Some(new InetSocketAddress(hostname, port)), timeout)
  }

  private[akka] def newInstance[T](target: Class[T], actorRef: ActorRef, remoteAddress: Option[InetSocketAddress], timeout: Long): T = {
    val proxy = Proxy.newInstance(target, false, false)
    actorRef.actor.asInstanceOf[Dispatcher].initialize(target, proxy)
    actorRef.timeout = timeout
    if (remoteAddress.isDefined) actorRef.makeRemote(remoteAddress.get)
    AspectInitRegistry.register(proxy, AspectInit(target, actorRef, remoteAddress, timeout))
    actorRef.start
    proxy.asInstanceOf[T]
  }

  private[akka] def newInstance[T](intf: Class[T], target: AnyRef, actorRef: ActorRef, remoteAddress: Option[InetSocketAddress], timeout: Long): T = {
    val proxy = Proxy.newInstance(Array(intf), Array(target), false, false)
    actorRef.actor.asInstanceOf[Dispatcher].initialize(target.getClass, target)
    actorRef.timeout = timeout
    if (remoteAddress.isDefined) actorRef.makeRemote(remoteAddress.get)
    AspectInitRegistry.register(proxy, AspectInit(intf, actorRef, remoteAddress, timeout))
    actorRef.start
    proxy.asInstanceOf[T]
  }

  /**
   * Get the underlying dispatcher actor for the given active object.
   */
  def actorFor(obj: AnyRef): Option[ActorRef] =
    ActorRegistry.actorsFor(classOf[Dispatcher]).find(a => a.actor.asInstanceOf[Dispatcher].target == Some(obj))

  /**
   * Links an other active object to this active object.
   * @param supervisor the supervisor active object
   * @param supervised the active object to link
   */
  def link(supervisor: AnyRef, supervised: AnyRef) = {
    val supervisorActor = actorFor(supervisor).getOrElse(
      throw new IllegalStateException("Can't link when the supervisor is not an active object"))
    val supervisedActor = actorFor(supervised).getOrElse(
      throw new IllegalStateException("Can't link when the supervised is not an active object"))
    supervisorActor !! Link(supervisedActor)
  }

  /**
   * Links an other active object to this active object and sets the fault handling for the supervisor.
   * @param supervisor the supervisor active object
   * @param supervised the active object to link
   * @param handler fault handling strategy
   * @param trapExceptions array of exceptions that should be handled by the supervisor
   */
  def link(supervisor: AnyRef, supervised: AnyRef, handler: FaultHandlingStrategy, trapExceptions: Array[Class[_ <: Throwable]]) = {
    val supervisorActor = actorFor(supervisor).getOrElse(
      throw new IllegalStateException("Can't link when the supervisor is not an active object"))
    val supervisedActor = actorFor(supervised).getOrElse(
      throw new IllegalStateException("Can't link when the supervised is not an active object"))
    supervisorActor.trapExit = trapExceptions.toList
    supervisorActor.faultHandler = Some(handler)
    supervisorActor !! Link(supervisedActor)
  }

  /**
   * Unlink the supervised active object from the supervisor.
   * @param supervisor the supervisor active object
   * @param supervised the active object to unlink
   */
  def unlink(supervisor: AnyRef, supervised: AnyRef) = {
    val supervisorActor = actorFor(supervisor).getOrElse(
      throw new IllegalStateException("Can't unlink when the supervisor is not an active object"))
    val supervisedActor = actorFor(supervised).getOrElse(
      throw new IllegalStateException("Can't unlink when the supervised is not an active object"))
    supervisorActor !! Unlink(supervisedActor)
  }

  /**
   * Sets the trap exit for the given supervisor active object.
   * @param supervisor the supervisor active object
   * @param trapExceptions array of exceptions that should be handled by the supervisor
   */
  def trapExit(supervisor: AnyRef, trapExceptions: Array[Class[_ <: Throwable]]) = {
    val supervisorActor = actorFor(supervisor).getOrElse(
      throw new IllegalStateException("Can't set trap exceptions when the supervisor is not an active object"))
    supervisorActor.trapExit = trapExceptions.toList
    this
  }

  /**
   * Sets the fault handling strategy for the given supervisor active object.
   * @param supervisor the supervisor active object
   * @param handler fault handling strategy
   */
  def faultHandler(supervisor: AnyRef, handler: FaultHandlingStrategy) = {
    val supervisorActor = actorFor(supervisor).getOrElse(
      throw new IllegalStateException("Can't set fault handler when the supervisor is not an active object"))
    supervisorActor.faultHandler = Some(handler)
    this
  }

  private[akka] def supervise(restartStrategy: RestartStrategy, components: List[Supervise]): Supervisor =  
    Supervisor(SupervisorConfig(restartStrategy, components))
}

private[akka] object AspectInitRegistry {
  private val initializations = new java.util.concurrent.ConcurrentHashMap[AnyRef, AspectInit]

  def initFor(target: AnyRef) = {
    val init = initializations.get(target)
    initializations.remove(target)
    init
  }

  def register(target: AnyRef, init: AspectInit) = initializations.put(target, init)
}

private[akka] sealed case class AspectInit(
  val target: Class[_],
  val actorRef: ActorRef,
  val remoteAddress: Option[InetSocketAddress],
  val timeout: Long) {
  def this(target: Class[_], actorRef: ActorRef, timeout: Long) = this(target, actorRef, None, timeout)
}

/**
 * AspectWerkz Aspect that is turning POJOs into Active Object.
 * Is deployed on a 'per-instance' basis.
 *
 * @author <a href="http://jonasboner.com">Jonas Bon&#233;r</a>
 */
@Aspect("perInstance")
// TODO: add @shutdown callback to ActiveObject in which we get the Aspect through 'Aspects.aspectOf(MyAspect.class, targetInstance)' and shuts down the Dispatcher actor
private[akka] sealed class ActiveObjectAspect {
  @volatile private var isInitialized = false
  private var target: Class[_] = _
  private var actorRef: ActorRef = _
  private var remoteAddress: Option[InetSocketAddress] = _
  private var timeout: Long = _

  @Around("execution(* *.*(..))")
  def invoke(joinPoint: JoinPoint): AnyRef = {
    if (!isInitialized) {
      val init = AspectInitRegistry.initFor(joinPoint.getThis)
      target = init.target
      actorRef = init.actorRef
      remoteAddress = init.remoteAddress
      timeout = init.timeout
      isInitialized = true
    }
    dispatch(joinPoint)
  }

  private def dispatch(joinPoint: JoinPoint) = {
    if (remoteAddress.isDefined) remoteDispatch(joinPoint)
    else localDispatch(joinPoint)
  }

  private def localDispatch(joinPoint: JoinPoint): AnyRef = {
    val rtti = joinPoint.getRtti.asInstanceOf[MethodRtti]
    if (isOneWay(rtti)) {
      (actorRef ! Invocation(joinPoint, true, true) ).asInstanceOf[AnyRef]
    }
    else {
      val result = actorRef !! (Invocation(joinPoint, false, isVoid(rtti)), timeout)
      if (result.isDefined) result.get
      else throw new IllegalStateException("No result defined for invocation [" + joinPoint + "]")
    }
  }

  private def remoteDispatch(joinPoint: JoinPoint): AnyRef = {
    val rtti = joinPoint.getRtti.asInstanceOf[MethodRtti]
    val oneWay_? = isOneWay(rtti) || isVoid(rtti)
    val (message: Array[AnyRef], isEscaped) = escapeArguments(rtti.getParameterValues)
    val requestBuilder = RemoteRequestProtocol.newBuilder
      .setId(RemoteRequestProtocolIdFactory.nextId)
      .setMethod(rtti.getMethod.getName)
      .setTarget(target.getName)
      .setUuid(actorRef.uuid)
      .setTimeout(timeout)
      .setIsActor(false)
      .setIsOneWay(oneWay_?)
      .setIsEscaped(false)
    RemoteProtocolBuilder.setMessage(message, requestBuilder)
    val id = actorRef.registerSupervisorAsRemoteActor
    if (id.isDefined) requestBuilder.setSupervisorUuid(id.get)
    val remoteMessage = requestBuilder.build
    val future = RemoteClient.clientFor(remoteAddress.get).send(remoteMessage, None)
    if (oneWay_?) null // for void methods
    else {
      if (future.isDefined) {
        future.get.await
        val result = getResultOrThrowException(future.get)
        if (result.isDefined) result.get
        else throw new IllegalStateException("No result returned from call to [" + joinPoint + "]")
      } else throw new IllegalStateException("No future returned from call to [" + joinPoint + "]")
    }
  }

  private def getResultOrThrowException[T](future: Future[T]): Option[T] =
    if (future.exception.isDefined) {
      val (_, cause) = future.exception.get
      throw cause
    } else future.result

  private def isOneWay(rtti: MethodRtti) = rtti.getMethod.isAnnotationPresent(Annotations.oneway)

  private def isVoid(rtti: MethodRtti) = rtti.getMethod.getReturnType == java.lang.Void.TYPE

  private def escapeArguments(args: Array[AnyRef]): Tuple2[Array[AnyRef], Boolean] = {
    var isEscaped = false
    val escapedArgs = for (arg <- args) yield {
      val clazz = arg.getClass
      if (clazz.getName.contains(ActiveObject.AW_PROXY_PREFIX)) {
        isEscaped = true
        ActiveObject.AW_PROXY_PREFIX + clazz.getSuperclass.getName
      } else arg
    }
    (escapedArgs, isEscaped)
  }
}

/**
 * Represents a snapshot of the current invocation.
 *
 * @author <a href="http://jonasboner.com">Jonas Bon&#233;r</a>
 */
@serializable private[akka] case class Invocation(joinPoint: JoinPoint, isOneWay: Boolean, isVoid: Boolean) {

  override def toString: String = synchronized {
    "Invocation [joinPoint: " + joinPoint.toString + ", isOneWay: " + isOneWay + ", isVoid: " + isVoid + "]"
  }

  override def hashCode: Int = synchronized {
    var result = HashCode.SEED
    result = HashCode.hash(result, joinPoint)
    result = HashCode.hash(result, isOneWay)
    result = HashCode.hash(result, isVoid)
    result
  }

  override def equals(that: Any): Boolean = synchronized {
    that != null &&
    that.isInstanceOf[Invocation] &&
    that.asInstanceOf[Invocation].joinPoint == joinPoint &&
    that.asInstanceOf[Invocation].isOneWay == isOneWay &&
    that.asInstanceOf[Invocation].isVoid == isVoid
  }
}

object Dispatcher {
  val ZERO_ITEM_CLASS_ARRAY = Array[Class[_]]()
  val ZERO_ITEM_OBJECT_ARRAY = Array[Object]()
}

/**
 * Generic Actor managing Invocation dispatch, transaction and error management.
 *
 * @author <a href="http://jonasboner.com">Jonas Bon&#233;r</a>
 */
private[akka] class Dispatcher(transactionalRequired: Boolean, val callbacks: Option[RestartCallbacks]) extends Actor {
  import Dispatcher._

  private[actor] var target: Option[AnyRef] = None
  private var preRestart: Option[Method] = None
  private var postRestart: Option[Method] = None
  private var initTxState: Option[Method] = None

  def this(transactionalRequired: Boolean) = this(transactionalRequired,None)

  private[actor] def initialize(targetClass: Class[_], targetInstance: AnyRef) = {
    if (transactionalRequired || targetClass.isAnnotationPresent(Annotations.transactionrequired)) self.makeTransactionRequired
    self.id = targetClass.getName
    target = Some(targetInstance)
    val methods = targetInstance.getClass.getDeclaredMethods.toList

    // See if we have any config define restart callbacks
    callbacks match {
      case None => {}
      case Some(RestartCallbacks(pre, post)) =>
        preRestart = Some(try {
          targetInstance.getClass.getDeclaredMethod(pre, ZERO_ITEM_CLASS_ARRAY: _*)
        } catch { case e => throw new IllegalStateException(
          "Could not find pre restart method [" + pre + "] \nin [" + 
          targetClass.getName + "]. \nIt must have a zero argument definition.") })
        postRestart = Some(try {
          targetInstance.getClass.getDeclaredMethod(post, ZERO_ITEM_CLASS_ARRAY: _*)
        } catch { case e => throw new IllegalStateException(
          "Could not find post restart method [" + post + "] \nin [" + 
          targetClass.getName + "]. \nIt must have a zero argument definition.") })
    }

    // See if we have any annotation defined restart callbacks
    if (!preRestart.isDefined) preRestart = methods.find(m => m.isAnnotationPresent(Annotations.prerestart))
    if (!postRestart.isDefined) postRestart = methods.find(m => m.isAnnotationPresent(Annotations.postrestart))

    if (preRestart.isDefined && preRestart.get.getParameterTypes.length != 0)
      throw new IllegalStateException(
        "Method annotated with @prerestart or defined as a restart callback in \n[" + 
        targetClass.getName + "] must have a zero argument definition")
    if (postRestart.isDefined && postRestart.get.getParameterTypes.length != 0)
      throw new IllegalStateException(
        "Method annotated with @postrestart or defined as a restart callback in \n[" + 
        targetClass.getName + "] must have a zero argument definition")

    if (preRestart.isDefined) preRestart.get.setAccessible(true)
    if (postRestart.isDefined) postRestart.get.setAccessible(true)

    // see if we have a method annotated with @inittransactionalstate, if so invoke it
    initTxState = methods.find(m => m.isAnnotationPresent(Annotations.inittransactionalstate))
    if (initTxState.isDefined && initTxState.get.getParameterTypes.length != 0) 
      throw new IllegalStateException("Method annotated with @inittransactionalstate must have a zero argument definition")
    if (initTxState.isDefined) initTxState.get.setAccessible(true)
  }

  def receive = {
    case Invocation(joinPoint, isOneWay, _) =>
      if (Actor.SERIALIZE_MESSAGES) serializeArguments(joinPoint)
      if (isOneWay) joinPoint.proceed
      else self.reply(joinPoint.proceed)
    // Jan Kronquist: started work on issue 121
    case Link(target)   => self.link(target)
    case Unlink(target) => self.unlink(target)
    case unexpected =>
      throw new IllegalStateException("Unexpected message [" + unexpected + "] sent to [" + this + "]")
  }

  override def preRestart(reason: Throwable) {
    try {
      if (preRestart.isDefined) preRestart.get.invoke(target.get, ZERO_ITEM_OBJECT_ARRAY: _*)
    } catch { case e: InvocationTargetException => throw e.getCause }
  }

  override def postRestart(reason: Throwable) {
    try {
      if (postRestart.isDefined) postRestart.get.invoke(target.get, ZERO_ITEM_OBJECT_ARRAY: _*)
    } catch { case e: InvocationTargetException => throw e.getCause }
  }

  override def initTransactionalState = {
    try {
      if (initTxState.isDefined && target.isDefined) initTxState.get.invoke(target.get, ZERO_ITEM_OBJECT_ARRAY: _*)
    } catch { case e: InvocationTargetException => throw e.getCause }
  }

  private def serializeArguments(joinPoint: JoinPoint) = {
    val args = joinPoint.getRtti.asInstanceOf[MethodRtti].getParameterValues
    var unserializable = false
    var hasMutableArgument = false
    for (arg <- args.toList) {
      if (!arg.isInstanceOf[String] &&
        !arg.isInstanceOf[Byte] &&
        !arg.isInstanceOf[Int] &&
        !arg.isInstanceOf[Long] &&
        !arg.isInstanceOf[Float] &&
        !arg.isInstanceOf[Double] &&
        !arg.isInstanceOf[Boolean] &&
        !arg.isInstanceOf[Char] &&
        !arg.isInstanceOf[java.lang.Byte] &&
        !arg.isInstanceOf[java.lang.Integer] &&
        !arg.isInstanceOf[java.lang.Long] &&
        !arg.isInstanceOf[java.lang.Float] &&
        !arg.isInstanceOf[java.lang.Double] &&
        !arg.isInstanceOf[java.lang.Boolean] &&
        !arg.isInstanceOf[java.lang.Character] &&
        !arg.getClass.isAnnotationPresent(Annotations.immutable)) {
        hasMutableArgument = true
      }
      if (arg.getClass.getName.contains(ActiveObject.AW_PROXY_PREFIX)) unserializable = true
    }
    if (!unserializable && hasMutableArgument) {
      val copyOfArgs = Serializer.Java.deepClone(args)
      joinPoint.getRtti.asInstanceOf[MethodRtti].setParameterValues(copyOfArgs.asInstanceOf[Array[AnyRef]])
    }
  }
}
