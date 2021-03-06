.. _untyped-actors-java:

Actors (Java)
=============

.. sidebar:: Contents

   .. contents:: :local:

Module stability: **SOLID**

The `Actor Model <http://en.wikipedia.org/wiki/Actor_model>`_ provides a higher level of abstraction for writing concurrent and distributed systems. It alleviates the developer from having to deal with explicit locking and thread management, making it easier to write correct concurrent and parallel systems. Actors were defined in the 1973 paper by Carl Hewitt but have been popularized by the Erlang language, and used for example at Ericsson with great success to build highly concurrent and reliable telecom systems.

Defining an Actor class
-----------------------

Actors in Java are created either by extending the 'UntypedActor' class and implementing the 'onReceive' method. This method takes the message as a parameter.

Here is an example:

.. code-block:: java

  import akka.actor.UntypedActor;
  import akka.event.EventHandler;

  public class SampleUntypedActor extends UntypedActor {

    public void onReceive(Object message) throws Exception {
      if (message instanceof String) 
        EventHandler.info(this, String.format("Received String message: %s",
          message));
      else 
        throw new IllegalArgumentException("Unknown message: " + message);
    }
  }

Creating Actors
^^^^^^^^^^^^^^^

Creating an Actor is done using the 'akka.actor.Actors.actorOf' factory method. This method returns a reference to the UntypedActor's ActorRef. This 'ActorRef' is an immutable serializable reference that you should use to communicate with the actor, send messages, link to it etc. This reference also functions as the context for the actor and holds run-time type information such as sender of the last message,

.. code-block:: java

  ActorRef myActor = Actors.actorOf(SampleUntypedActor.class);
  myActor.start();

Normally you would want to import the 'actorOf' method like this:

.. code-block:: java

  import static akka.actor.Actors.*;
  ActorRef myActor = actorOf(SampleUntypedActor.class);

To avoid prefix it with 'Actors' every time you use it.

You can also create & start the actor in one statement:

.. code-block:: java

  ActorRef myActor = actorOf(SampleUntypedActor.class).start();

The call to 'actorOf' returns an instance of 'ActorRef'. This is a handle to the 'UntypedActor' instance which you can use to interact with the Actor, like send messages to it etc. more on this shortly. The 'ActorRef' is immutable and has a one to one relationship with the Actor it represents. The 'ActorRef' is also serializable and network-aware. This means that you can serialize it, send it over the wire and use it on a remote host and it will still be representing the same Actor on the original node, across the network.

Creating Actors with non-default constructor
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

If your UntypedActor has a constructor that takes parameters then you can't create it using 'actorOf(clazz)'. Instead you can use a variant of 'actorOf' that takes an instance of an 'UntypedActorFactory' in which you can create the Actor in any way you like. If you use this method then you to make sure that no one can get a reference to the actor instance. If they can get a reference it then they can touch state directly in bypass the whole actor dispatching mechanism and create race conditions which can lead to corrupt data.

Here is an example:

.. code-block:: java

  ActorRef actor = actorOf(new UntypedActorFactory() {
    public UntypedActor create() {
      return new MyUntypedActor("service:name", 5);
     }
  });

This way of creating the Actor is also great for integrating with Dependency Injection (DI) frameworks like Guice or Spring.

UntypedActor context
--------------------

The UntypedActor base class contains almost no member fields or methods to invoke. It only has the 'onReceive(Object message)' method, which is defining the Actor's message handler, and some life-cycle callbacks that you can choose to implement:
## preStart
## postStop
## preRestart
## postRestart

Most of the API is in the UnypedActorRef a reference for the actor. This reference is available in the 'getContext()' method in the UntypedActor (or you can use its alias, the 'context()' method, if you prefer. Here, for example, you find methods to reply to messages, send yourself messages, define timeouts, fault tolerance etc., start and stop etc.

Identifying Actors
------------------

Each ActorRef has two methods:
* getContext().getUuid();
* getContext().getId();

The difference is that the 'uuid' is generated by the runtime, guaranteed to be unique and can't be modified. While the 'id' can be set by the user (using 'getContext().setId(...)', and defaults to Actor class name. You can retrieve Actors by both UUID and ID using the 'ActorRegistry', see the section further down for details.

Messages and immutability
-------------------------

**IMPORTANT**: Messages can be any kind of object but have to be immutable. Akka can’t enforce immutability (yet) so this has to be by convention.

Send messages
-------------

Messages are sent to an Actor through one of the 'send' methods.
* 'sendOneWay' means “fire-and-forget”, e.g. send a message asynchronously and return immediately.
* 'sendRequestReply' means “send-and-reply-eventually”, e.g. send a message asynchronously and wait for a reply through a Future. Here you can specify a timeout. Using timeouts is very important. If no timeout is specified then the actor’s default timeout (set by the 'getContext().setTimeout(..)' method in the 'ActorRef') is used. This method throws an 'ActorTimeoutException' if the call timed out.
* 'ask' sends a message asynchronously and returns a 'Future'.

In all these methods you have the option of passing along your 'ActorRef' context variable. Make it a practice of doing so because it will allow the receiver actors to be able to respond to your message, since the sender reference is sent along with the message.

Fire-forget
^^^^^^^^^^^

This is the preferred way of sending messages. No blocking waiting for a message. Give best concurrency and scalability characteristics.

.. code-block:: java

  actor.sendOneWay("Hello");

Or with the sender reference passed along:

.. code-block:: java

  actor.sendOneWay("Hello", getContext());

If invoked from within an Actor, then the sending actor reference will be implicitly passed along with the message and available to the receiving Actor in its 'getContext().getSender();' method. He can use this to reply to the original sender or use the 'getContext().reply(message);' method.

If invoked from an instance that is **not** an Actor there will be no implicit sender passed along the message and you will get an 'IllegalStateException' if you call 'getContext().reply(..)'.

Send-And-Receive-Eventually
^^^^^^^^^^^^^^^^^^^^^^^^^^^

Using 'sendRequestReply' will send a message to the receiving Actor asynchronously but it will wait for a reply on a 'Future', blocking the sender Actor until either:

* A reply is received, or
* The Future times out and an 'ActorTimeoutException' is thrown.

You can pass an explicit time-out to the 'sendRequestReply' method and if none is specified then the default time-out defined in the sender Actor will be used.

Here are some examples:

.. code-block:: java

  UntypedActorRef actorRef = ...

  try {
    Object result = actorRef.sendRequestReply("Hello", getContext(), 1000);
    ... // handle reply
  } catch(ActorTimeoutException e) {
    ... // handle timeout
  }

Send-And-Receive-Future
^^^^^^^^^^^^^^^^^^^^^^^

Using 'ask' will send a message to the receiving Actor asynchronously and will immediately return a 'Future'.

.. code-block:: java

  Future future = actorRef.ask("Hello", getContext(), 1000);

The 'Future' interface looks like this:

.. code-block:: java

  interface Future<T> {
    void await();
    boolean isCompleted();
    boolean isExpired();
    long timeoutInNanos();
    Option<T> result();
    Option<Throwable> exception();
    Future<T> onComplete(Procedure<Future<T>> procedure);
  }

So the normal way of working with futures is something like this:

.. code-block:: java

  Future future = actorRef.ask("Hello", getContext(), 1000);
  future.await();
  if (future.isCompleted()) {
    Option resultOption = future.result();
    if (resultOption.isDefined()) {
      Object result = resultOption.get();
      ...
    }
    ... // whatever
  }

The 'onComplete' callback can be used to register a callback to get a notification when the Future completes. Gives you a way to avoid blocking.

Forward message
^^^^^^^^^^^^^^^

You can forward a message from one actor to another. This means that the original sender address/reference is maintained even though the message is going through a 'mediator'. This can be useful when writing actors that work as routers, load-balancers, replicators etc. You need to pass along your ActorRef context variable as well.

.. code-block:: java

  getContext().forward(message, getContext());

Receive messages
----------------

When an actor receives a message it is passed into the 'onReceive' method, this is an abstract method on the 'UntypedActor' base class that needs to be defined.

Here is an example:

.. code-block:: java

  public class SampleUntypedActor extends UntypedActor {

    public void onReceive(Object message) throws Exception {
      if (message instanceof String) 
        EventHandler.info(this, String.format("Received String message: %s", message));
      else 
        throw new IllegalArgumentException("Unknown message: " + message);
    }
  }

Reply to messages
-----------------

Reply using the channel
^^^^^^^^^^^^^^^^^^^^^^^

If you want to have a handle to an object to whom you can reply to the message, you can use the Channel abstraction.
Simply call getContext().channel() and then you can forward that to others, store it away or otherwise until you want to reply,
which you do by Channel.sendOneWay(msg)

.. code-block:: java

  public void onReceive(Object message) throws Exception {
    if (message instanceof String) {
      String msg = (String)message;
      if (msg.equals("Hello")) {
        // Reply to original sender of message using the channel
        getContext().channel().sendOneWaySafe(msg + " from " + getContext().getUuid());
      }
    }
  }

We recommend that you as first choice use the channel abstraction instead of the other ways described in the following sections.

Reply using the 'replySafe' and 'replyUnsafe' methods
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

If you want to send a message back to the original sender of the message you just received then you can use the 'getContext().replyUnsafe(..)' method.

.. code-block:: java

  public void onReceive(Object message) throws Exception {
    if (message instanceof String) {
      String msg = (String)message;
      if (msg.equals("Hello")) {
        // Reply to original sender of message using the 'replyUnsafe' method
        getContext().replyUnsafe(msg + " from " + getContext().getUuid());
      }
    }
  }

In this case we will a reply back to the Actor that sent the message.

The 'replyUnsafe' method throws an 'IllegalStateException' if unable to determine what to reply to, e.g. the sender has not been passed along with the message when invoking one of 'send*' methods. You can also use the more forgiving 'replySafe' method which returns 'true' if reply was sent, and 'false' if unable to determine what to reply to.

.. code-block:: java

  public void onReceive(Object message) throws Exception {
    if (message instanceof String) {
      String msg = (String)message;
      if (msg.equals("Hello")) {
        // Reply to original sender of message using the 'replyUnsafe' method
        if (getContext().replySafe(msg + " from " + getContext().getUuid())) ... // success
        else ... // handle failure
      }
    }
  }

Summary of reply semantics and options
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

* ``getContext().reply(...)`` can be used to reply to an ``Actor`` or a
  ``Future`` from within an actor; the current actor will be passed as reply
  channel if the current channel supports this.
* ``getContext().channel`` is a reference providing an abstraction for the
  reply channel; this reference may be passed to other actors or used by
  non-actor code.

.. note::

  There used to be two methods for determining the sending Actor or Future for the current invocation:

  * ``getContext().getSender()`` yielded a :class:`Option[ActorRef]`
  * ``getContext().getSenderFuture()`` yielded a :class:`Option[CompletableFuture[Any]]`

  These two concepts have been unified into the ``channel``. If you need to
  know the nature of the channel, you may do so using instance tests::

    if (getContext().channel() instanceof ActorRef) {
      ...
    } else if (getContext().channel() instanceof ActorPromise) {
      ...
    }

Promise represents the write-side of a Future, enabled by the methods

* completeWithResult(..)
* completeWithException(..)

Starting actors
---------------

Actors are started by invoking the ‘start’ method.

.. code-block:: java

  ActorRef actor = actorOf(SampleUntypedActor.class);
  myActor.start();

You can create and start the Actor in a one liner like this:

.. code-block:: java

  ActorRef actor = actorOf(SampleUntypedActor.class).start();

When you start the actor then it will automatically call the 'preStart' callback method on the 'UntypedActor'. This is an excellent place to add initialization code for the actor.

.. code-block:: java

  @Override
  void preStart() {
    ... // initialization code
  }

Stopping actors
---------------

Actors are stopped by invoking the ‘stop’ method.

.. code-block:: java

  actor.stop();

When stop is called then a call to the ‘postStop’ callback method will take place. The Actor can use this callback to implement shutdown behavior.

.. code-block:: java

  @Override
  void postStop() {
    ... // clean up resources
  }

You can shut down all Actors in the system by invoking:

.. code-block:: java

  Actors.registry().shutdownAll();

PoisonPill
----------

You can also send an actor the akka.actor.PoisonPill message, which will stop the actor when the message is processed.
If the sender is a Future, the Future will be completed with an akka.actor.ActorKilledException("PoisonPill")

Use it like this:

.. code-block:: java

  import static akka.actor.Actors.*;
  
  actor.sendOneWay(poisonPill());

Killing an Actor
----------------

You can kill an actor by sending a 'new Kill()' message. This will restart the actor through regular supervisor semantics.

Use it like this:

.. code-block:: java

  import static akka.actor.Actors.*;

  // kill the actor called 'victim'
   victim.sendOneWay(kill());

Actor life-cycle
----------------

The actor has a well-defined non-circular life-cycle.

::

  NEW (newly created actor) - can't receive messages (yet)
      => STARTED (when 'start' is invoked) - can receive messages
          => SHUT DOWN (when 'exit' or 'stop' is invoked) - can't do anything

