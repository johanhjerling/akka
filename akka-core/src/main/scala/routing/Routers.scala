/**
 * Copyright (C) 2009-2010 Scalable Solutions AB <http://scalablesolutions.se>
 */

package se.scalablesolutions.akka.patterns

import se.scalablesolutions.akka.actor.{Actor, ActorRef}

/** A Dispatcher is a trait whose purpose is to route incoming messages to actors
 */
trait Dispatcher { this: Actor =>
//  implicit val sender = Some(self)

  protected def transform(msg: Any): Any = msg

  protected def routes: PartialFunction[Any, ActorRef]

  protected def dispatch: Receive = {
    case a if routes.isDefinedAt(a) =>
      if (self.replyTo.isDefined) routes(a).forward(transform(a))(Some(self))
      else routes(a).!(transform(a))(None)
  }

  def receive = dispatch
}

/** A LoadBalancer is a specialized kind of Dispatcher, 
 *  that is supplied an InfiniteIterator of targets
 *  to dispatch incoming messages to
 */
trait LoadBalancer extends Dispatcher { self: Actor =>
  protected def seq: InfiniteIterator[ActorRef]

  protected def routes = { case x if seq.hasNext => seq.next }
}