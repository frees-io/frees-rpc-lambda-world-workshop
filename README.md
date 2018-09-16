# My Smart Home workshop

<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->

- [What are we going to build?](#what-are-we-going-to-build)
- [Basic Freestyle-RPC Structure](#basic-freestyle-rpc-structure)
  - [How to run it](#how-to-run-it)
  - [Project structure](#project-structure)
    - [Protocol](#protocol)
    - [Server](#server)
    - [Client](#client)
- [Evolving the Avro schema](#evolving-the-avro-schema)
  - [Protocol](#protocol-1)
- [Unary RPC service: `IsEmpty`](#unary-rpc-service-isempty)
  - [Protocol](#protocol-2)
  - [Server](#server-1)
  - [Client](#client-1)
  - [Result](#result)
- [Server-streaming RPC service: `GetTemperature`](#server-streaming-rpc-service-gettemperature)
  - [Protocol](#protocol-3)
  - [Server](#server-2)
  - [Client](#client-2)
  - [Result](#result-1)
- [Bidirectional streaming RPC service: `comingBackMode`](#bidirectional-streaming-rpc-service-comingbackmode)
  - [Protocol](#protocol-4)
  - [Server](#server-3)
  - [Client](#client-3)
  - [Result](#result-2)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->


## What are we going to build?

During the course of this workshop, we are going to build a couple of purely functional microservices, which are going to interact with each other in different ways but always via the RPC protocol. One as a server will play the role of a smart home and the other will be a client, a mobile app for instance, and the interactions will be:

- `IsEmpty`: Will be a unary RPC, that means that the smart home will return a single response to each request from the mobile, to let it know if there is anybody inside the home or there isn't.

- `getTemperature`: Will be a unidirectional streaming service from the server, where the smart home will return a stream of temperature values in real-time after a single request from the mobile.

- `comingBackMode`: Will be a bidirectional streaming service, where the mobile app sends a stream of location coordinates and the smart home emits in streaming a list of operations that are being triggered. For instance:
   - If the client is about 30 minutes to come back, the home can start heating the living room and increase the power of the hot water heater.
   - If the client is only a 2-minute walk away, the home can turn some lights on and turn the irrigation system off.
   - If the client is in front of the main door, this can be unlocked and the alarms disabled.

## Basic Freestyle-RPC Structure

We are going to use the `rpc-server-client-pb` giter8 template to create the basic project structure, which provides a good basis to build upon. In this case, the template creates a multimodule project, with:
- The RPC protocol, which is very simple. It exposes a service to lookup a person given a name.
- The server, which with implements an interpreter of the service defined by the protocol and it runs an RPC server.
- The client, which consumes the RPC endpoint against the server, and it uses the protocol to know the schema.

To start:

```bash
sbt new frees-io/rpc-server-client-pb.g8
...
name [Project Name]: SmartHome
projectDescription [Project Description]: My SmartHome app
project [project-name]: smarthome
package [org.mycompany]: com.fortyseven
freesRPCVersion [0.14.1]:

Template applied in ./smarthome
```

### How to run it

Run the server:

```bash
sbt runServer
```

And the log will show:

```bash
INFO  - ServiceName(seedServer) - Starting app.server at Host(localhost):Port(19683)
```

then, run the client:

```bash
sbt runClient
```

The client should log:

```bash
INFO  - Created new RPC client for (localhost,19683)
INFO  - Request: foo
INFO  - Result: PeopleResponse(Person(foo,24))
INFO  - Request: bar
INFO  - Result: PeopleResponse(Person(bar,9))
INFO  - Request: baz
INFO  - Result: PeopleResponse(Person(baz,17))
INFO  - Removed 1 RPC clients from cache.
```

And the server:

```bash
INFO  - PeopleService - Request: PeopleRequest(foo)
INFO  - PeopleService - Sending response: Person(foo,24)
INFO  - PeopleService - Request: PeopleRequest(bar)
INFO  - PeopleService - Sending response: Person(bar,9)
INFO  - PeopleService - Request: PeopleRequest(baz)
INFO  - PeopleService - Sending response: Person(baz,17)
```

### Project structure

```bash
.
├── LICENSE
├── NOTICE.md
├── README.md
├── build.sbt
├── version.sbt
├── commons
├── project
├── client
│   └── src
│       └── main
│           ├── resources
│           │   └── logback.xml
│           └── scala
│               ├── ClientApp.scala
│               ├── ClientRPC.scala
│               └── PeopleServiceApi.scala
├── protocol
│   └── src
│       └── main
│           └── resources
│               ├── People.avdl
│               └── PeopleService.avdl
└── server
    └── src
        └── main
            └── scala
                ├── PeopleRepository.scala
                ├── PeopleServiceHandler.scala
                └── ServerApp.scala
```

#### Protocol

The protocol module includes the definition of the service and the messages that will be used both by the server and the client:

```bash
├── protocol
│   └── src
│       └── main
│           └── resources
│               ├── People.avdl
│               └── PeopleService.avdl
```

**_People.avdl_**

In this initial example, which the app only exposes a service to retrieve persons by a given name, we need to define the models that are going to "flow through the wire", and in this case we are using Avro Schema Definition:

```scala
protocol People {

  record Person {
    string name;
    int age;
  }

  record PeopleRequest {
    string name;
  }

  record PeopleResponse {
    Person person;
  }

}
```

**_PeopleService.avdl_**

And finally, we have to define the protocol. In this case is just an operation called `getPerson` that accepts a `PeopleRequest` and returns a `PeopleResponse`:

```scala
protocol PeopleService {
  import idl "People.avdl";
  PeopleResponse getPerson(PeopleRequest request);
}
```

#### Server

The server tackles mainly a couple of purposes: To run the RPC server and provide an interpreter to the service defined in the protocol.

```scala
└── server
    └── src
        └── main
            └── scala
                ├── PeopleRepository.scala
                ├── PeopleServiceHandler.scala
                └── ServerApp.scala
```

**_PoepleServiceHandler.scala_**

This is the interpretation of the protocol `PeopleService`. In this case, the `getPerson` operation returns the person retrieved by the `PeopleRepository`, which represents a database interaction.

```scala
class PeopleServiceHandler[F[_]: Sync: Logger: PeopleRepository] extends PeopleService[F] {
  val serviceName = "PeopleService"

  override def getPerson(request: PeopleRequest): F[PeopleResponse] =
    for {
      _      <- Logger[F].info(s"$serviceName - Request: $request")
      person <- PeopleRepository[F].getPerson(request.name)
      _      <- Logger[F].info(s"$serviceName - Sending response: $person")
    } yield PeopleResponse(person)
}
```

**_ServerApp.scala_**

The implementation of the `serverStream` leverages the features of **GrpcServer** to deal with servers.

```scala
implicit val PS: PeopleService[F] = new PeopleServiceHandler[F]

val grpcConfigs: List[GrpcConfig] = List(AddService(PeopleService.bindService[F]))

Stream.eval(
  for {
    server <- GrpcServer.default[F](config.port.value, grpcConfigs)
    _ <- Logger[F].info(s"${config.name} - Starting app.server at ${config.host}:${config.port}")
    exitCode <- GrpcServer.server(server).as(StreamApp.ExitCode.Success)
  } yield exitCode
)
```

#### Client

In this initial version of the client, it just runs a client for the `PeopleService` and it injects it in the streaming flow of the app.

```scala
├── client
│   └── src
│       └── main
│           ├── resources
│           │   └── logback.xml
│           └── scala
│               ├── ClientApp.scala
│               ├── ClientRPC.scala
│               └── PeopleServiceApi.scala
```

**_PeopleServiceApi.scala_**

This algebra is the via to connect to the server through the RPC client, using some Freestyle-RPC magic.

```scala
trait PeopleServiceApi[F[_]] {
  def getPersonByName(name: String): F[Person]
}

object PeopleServiceApi {

  def apply[F[_]: Effect](clientRPCF: F[PeopleService.Client[F]])(implicit L: Logger[F]):PeopleServiceApi[F] =
    new PeopleServiceApi[F] {
      override def getPersonByName(name: String): F[Person] =
        for {
          clientRPC <- clientRPCF
          _         <- L.info(s"Request: $name")
          result    <- clientRPC.getPerson(PeopleRequest(name))
          _         <- L.info(s"Result: $result")
        } yield result.person
    }

  def createInstance[F[_]: Effect](
      hostname: String,
      port: Int,
      sslEnabled: Boolean = false,
      tryToRemoveUnusedEvery: FiniteDuration = 30.minutes,
      removeUnusedAfter: FiniteDuration = 1.hour)(
      implicit L: Logger[F],
      TM: Timer[F],
      S: Scheduler): fs2.Stream[F, PeopleServiceApi[F]] = ???
}
```

**_ClientRPC.scala_**

This object provides an RPC client for a given tuple of host and port. It's used in `PeopleServiceApi`.

**_ClientApp.scala_**

Similar to `ServerApp`, this app instantiates the logger, the RPC client and it calls to `getPersonByName` as soon as it starts running.

```scala
for {
  peopleApi <- PeopleServiceApi.createInstance(config.host.value, config.port.value)
  exitCode <- Stream
    .eval(List("foo", "bar", "baz").traverse[F, Person](peopleApi.getPersonByName))
    .as(StreamApp.ExitCode.Success)
} yield exitCode
```

## Evolving the Avro schema

As we have seen before, both client and server are using the same common protocol defined via Avro schema, which is an ideal scenario but realistically speaking the server side might need to add certainly changes in the model. Then how the server can preserve the compatibility with clients that are still using the old model?

Thanks to the Avro definitions we can add evolutions to the models in a safety way, keeping all the clients fully compatible but obviously, there are some limited operations that can't be done, like removing a field in a response model or adding a new required field to a request object.

To illustrate that non-updated clients are able to keep interacting with evolved servers, we'll just add a new field `phone` to `Person`.

### Protocol

Let's add a new evolution to the models described in the protocol

**_People.avdl_**


```scala
protocol People {

  record Person {
    string name;
    int age;
    string phone;
  }

  record PeopleRequest {
    string name;
  }

  record PeopleResponse {
    Person person;
  }

}
```

We can now run the server app using this new version, and the client app with the previous one, and the requests should have been processed properly on both sides.

As we can see, the client digests `Person`s instances included in the responses as expected:

```scala
INFO  - Created new RPC client for (localhost,19683)
INFO  - Request: foo
INFO  - Result: PeopleResponse(Person(foo,24))
INFO  - Request: bar
INFO  - Result: PeopleResponse(Person(bar,9))
INFO  - Request: baz
INFO  - Result: PeopleResponse(Person(baz,17))
INFO  - Removed 1 RPC clients from cache.
```

Even when actually the server is including the telephone numbers at them:

```scala
INFO  - PeopleService - Request: PeopleRequest(foo)
INFO  - PeopleService - Sending response: Person(foo,24,(206) 198-8396)
INFO  - PeopleService - Request: PeopleRequest(bar)
INFO  - PeopleService - Sending response: Person(bar,9,(206) 740-2096)
INFO  - PeopleService - Request: PeopleRequest(baz)
INFO  - PeopleService - Sending response: Person(baz,17,(206) 812-1984)
```

## Unary RPC service: `IsEmpty`

Having said this, now it's the right moment to get started to develop the features of the SmartHome, and discard the `People` stuff. As we said above, we want also to build a unary RPC service to let clients know if there is somebody in the home or there is not.

### Protocol

In order to show another way to define protocols, we are going to express our models and services using directly Scala code, and using **ProtocolBuffer** as serialiser instead of **Avro**.

So the protocol module can adopt know this shape (of course we should also discard the `idlgens` references at `ProjectPlugin.scala` and `plugins.sbt`):

```scala
├── protocol
│   └── src
│       └── main
│           └── scala
│               └── protocol
│                   ├── Messages.scala
│                   └── SmartHomeService.scala
```

**_Messages.scala_**

Where we defined the messages flowing through the wire:

```scala
@message
final case class IsEmptyRequest()

@message
final case class IsEmptyResponse(result: Boolean)
```

**_SmartHomeService.scala_**

Where we defined interface of the RPC service:

```scala
@service(Protobuf) trait SmartHomeService[F[_]] {

  def isEmpty(request: IsEmptyRequest): F[IsEmptyResponse]

}
```

### Server

Now, we have to implement an interpreter for the new service `SmartHomeService`:

```scala
class SmartHomeServiceHandler[F[_]: Sync: Logger] extends SmartHomeService[F] {
  val serviceName = "SmartHomeService"

  override def isEmpty(request: IsEmptyRequest): F[IsEmptyResponse] =
    Logger[F].info(s"$serviceName - Request: $request").as(IsEmptyResponse(true))

}
```

And bind it to the gRPC server:

```scala
val grpcConfigs: List[GrpcConfig] = List(AddService(SmartHomeService.bindService[F]))
```

### Client

And the client, of course, needs an algebra to describe the same operation:

```scala
trait SmartHomeServiceApi[F[_]] {
  def isEmpty(): F[Boolean]
}
```

That will be called when the app is running

```scala
for {
  serviceApi <- SmartHomeServiceApi.createInstance(config.host.value, config.port.value)
  _          <- Stream.eval(serviceApi.isEmpty)
} yield StreamApp.ExitCode.Success
```

### Result

When we run the client now with `sbt runClient` we get:

```bash
INFO  - Created new RPC client for (localhost,19683)
INFO  - Result: IsEmptyResponse(true)
INFO  - Removed 1 RPC clients from cache.
```

And the server log the request as expected:

```bash
INFO  - SmartHomeService - Request: IsEmptyRequest()
```


## Server-streaming RPC service: `GetTemperature`

Following the established plan, the next step is building the service that returns a stream of temperature values, to let clients subscribe to collect real-time info.

### Protocol

As usual we should add this operation in the protocol.

**_Messages.scala_**

Adding new models:

```scala
case class TemperatureUnit(value: String) extends AnyVal
case class Temperature(value: Double, unit: TemperatureUnit)
```

**_SmartHomeService.scala_**

And the `getTemperature` operation:

```scala
@service(Protobuf) trait SmartHomeService[F[_]] {

  def isEmpty(request: IsEmptyRequest): F[IsEmptyResponse]

  def getTemperature(empty: Empty.type): Stream[F, Temperature]
}
```

### Server

If we want to emit a stream of `Temperature` values,  we would be well advised to develop a producer of `Temperature` in the server side. For instance:

```scala
trait TemperatureReader[F[_]] {
  def sendSamples: Stream[F, Temperature]
}

object TemperatureReader {
  implicit def instance[F[_]: Sync: Logger: Timer]: TemperatureReader[F] =
    new TemperatureReader[F] {
      val seed = Temperature(77d, TemperatureUnit("Fahrenheit"))

      def readTemperature(current: Temperature): F[Temperature] =
        Timer[F]
          .sleep(1.second)
          .flatMap(_ =>
            Sync[F].delay {
              val increment: Double = Random.nextDouble() / 2d
              val signal            = if (Random.nextBoolean()) 1 else -1
              val currentValue      = current.value

              current.copy(
                value = BigDecimal(currentValue + (signal * increment))
                  .setScale(2, RoundingMode.HALF_UP)
                  .doubleValue)
          })

      override def sendSamples: Stream[F, Temperature] =
        Stream.iterateEval(seed) { t =>
          Logger[F].info(s"* New Temperature 👍  --> $t").flatMap(_ => readTemperature(t))
        }
    }

  def apply[F[_]](implicit ev: TemperatureReader[F]): TemperatureReader[F] = ev
}
```

And this can be returned as response of the new service, in the interpreter.

```scala
override def getTemperature(empty: Empty.type): Stream[F, Temperature] = for {
  _            <- Stream.eval(Logger[F].info(s"$serviceName - getTemperature Request"))
  temperatures <- TemperatureReader[F].sendSamples.take(20)
} yield temperatures
```

### Client

We have nothing less than adapt the client to consume the new service when it starting up. To this, a couple of changes are needed:

Firstly we should enrich the algebra

```scala
trait SmartHomeServiceApi[F[_]] {

  def isEmpty(): F[Boolean]

  def getTemperature(): Stream[F, Temperature]

}
```

Whose interpretation could be:

```scala
def getTemperature: Stream[F, TemperaturesSummary] = {
  for {
    client      <- Stream.eval(clientF)
    temperature <- client.getTemperature(Empty)
    _           <- Stream.eval(L.info(s"* Received new temperature: 👍 --> $temperature"))
  } yield temperature
}.fold(TemperaturesSummary.empty)((summary, temperature) => summary.append(temperature))
```

Basically, we are logging the incoming values and at the end we calculate the average of those values.

Now, the client app calls to both services: `isEmpty` and `getTemperature`.
And finally, to call it:

```scala
for {
  serviceApi  <- SmartHomeServiceApi.createInstance(config.host.value, config.port.value)
  _           <- Stream.eval(serviceApi.isEmpty)
  summary     <- serviceApi.getTemperature
  _           <- Stream.eval(Logger[F].info(s"The average temperature is: ${summary.averageTemperature}"))
} yield StreamApp.ExitCode.Success
```

### Result

When we run the client now with `sbt runClient` we get:

```bash
INFO  - Created new RPC client for (localhost,19683)
INFO  - Result: IsEmptyResponse(true)
INFO  - * Received new temperature: 👍  --> Temperature(77.0,TemperatureUnit(Fahrenheit))
INFO  - * Received new temperature: 👍  --> Temperature(77.25,TemperatureUnit(Fahrenheit))
INFO  - * Received new temperature: 👍  --> Temperature(77.58,TemperatureUnit(Fahrenheit))
INFO  - * Received new temperature: 👍  --> Temperature(78.02,TemperatureUnit(Fahrenheit))
INFO  - * Received new temperature: 👍  --> Temperature(77.67,TemperatureUnit(Fahrenheit))
INFO  - * Received new temperature: 👍  --> Temperature(77.5,TemperatureUnit(Fahrenheit))
INFO  - * Received new temperature: 👍  --> Temperature(77.58,TemperatureUnit(Fahrenheit))
INFO  - * Received new temperature: 👍  --> Temperature(77.15,TemperatureUnit(Fahrenheit))
INFO  - * Received new temperature: 👍  --> Temperature(76.66,TemperatureUnit(Fahrenheit))
INFO  - * Received new temperature: 👍  --> Temperature(76.45,TemperatureUnit(Fahrenheit))
INFO  - * Received new temperature: 👍  --> Temperature(76.77,TemperatureUnit(Fahrenheit))
INFO  - * Received new temperature: 👍  --> Temperature(76.74,TemperatureUnit(Fahrenheit))
INFO  - * Received new temperature: 👍  --> Temperature(76.41,TemperatureUnit(Fahrenheit))
INFO  - * Received new temperature: 👍  --> Temperature(76.59,TemperatureUnit(Fahrenheit))
INFO  - * Received new temperature: 👍  --> Temperature(76.77,TemperatureUnit(Fahrenheit))
INFO  - * Received new temperature: 👍  --> Temperature(76.49,TemperatureUnit(Fahrenheit))
INFO  - * Received new temperature: 👍  --> Temperature(76.04,TemperatureUnit(Fahrenheit))
INFO  - * Received new temperature: 👍  --> Temperature(76.42,TemperatureUnit(Fahrenheit))
INFO  - * Received new temperature: 👍  --> Temperature(75.95,TemperatureUnit(Fahrenheit))
INFO  - * Received new temperature: 👍  --> Temperature(75.97,TemperatureUnit(Fahrenheit))
INFO  - The average temperature is: Temperature(76.85,TemperatureUnit(Fahrenheit))
INFO  - Removed 1 RPC clients from cache.
```

And the server log the request as expected:

```bash
INFO  - ServiceName(seedServer) - Starting app.server at Host(localhost):Port(19683)
INFO  - SmartHomeService - Request: IsEmptyRequest()
INFO  - SmartHomeService - getTemperature Request
INFO  - * New Temperature 👍  --> Temperature(77.0,TemperatureUnit(Fahrenheit))
INFO  - * New Temperature 👍  --> Temperature(77.25,TemperatureUnit(Fahrenheit))
INFO  - * New Temperature 👍  --> Temperature(77.58,TemperatureUnit(Fahrenheit))
INFO  - * New Temperature 👍  --> Temperature(78.02,TemperatureUnit(Fahrenheit))
INFO  - * New Temperature 👍  --> Temperature(77.67,TemperatureUnit(Fahrenheit))
INFO  - * New Temperature 👍  --> Temperature(77.5,TemperatureUnit(Fahrenheit))
INFO  - * New Temperature 👍  --> Temperature(77.58,TemperatureUnit(Fahrenheit))
INFO  - * New Temperature 👍  --> Temperature(77.15,TemperatureUnit(Fahrenheit))
INFO  - * New Temperature 👍  --> Temperature(76.66,TemperatureUnit(Fahrenheit))
INFO  - * New Temperature 👍  --> Temperature(76.45,TemperatureUnit(Fahrenheit))
INFO  - * New Temperature 👍  --> Temperature(76.77,TemperatureUnit(Fahrenheit))
INFO  - * New Temperature 👍  --> Temperature(76.74,TemperatureUnit(Fahrenheit))
INFO  - * New Temperature 👍  --> Temperature(76.41,TemperatureUnit(Fahrenheit))
INFO  - * New Temperature 👍  --> Temperature(76.59,TemperatureUnit(Fahrenheit))
INFO  - * New Temperature 👍  --> Temperature(76.77,TemperatureUnit(Fahrenheit))
INFO  - * New Temperature 👍  --> Temperature(76.49,TemperatureUnit(Fahrenheit))
INFO  - * New Temperature 👍  --> Temperature(76.04,TemperatureUnit(Fahrenheit))
INFO  - * New Temperature 👍  --> Temperature(76.42,TemperatureUnit(Fahrenheit))
INFO  - * New Temperature 👍  --> Temperature(75.95,TemperatureUnit(Fahrenheit))
```

## Bidirectional streaming RPC service: `comingBackMode`

To illustrate the bidirectional streaming, we are going to build a new service that makes the server react to real-time info provided by the client. In this case, as we said above, the client (the mobile app) will emit a stream of coordinates (latitude and longitude), and the server (the smart home) will trigger some actions according to the distance.

### Protocol

So let's add this service to the protocol.

**_Messages.scala_**

Adding new models:

```scala
case class Point(lat: Double, long: Double)
case class Location(currentLocation: Point, destination: Point, distanceToDestination: Double)
case class SmartHomeAction(description: String, isDone: Boolean)
@message
final case class ComingBackModeResponse(actions: List[SmartHomeAction])
```

**_SmartHomeService.scala_**

And the `comingBackMode` operation:

```scala
@service(Protobuf) trait SmartHomeService[F[_]] {

  def isEmpty(request: IsEmptyRequest): F[IsEmptyResponse]

  def getTemperature(empty: Empty.type): Stream[F, Temperature]

  def comingBackMode(request: Stream[F, Location]): Stream[F, ComingBackModeResponse]
}
```

### Server

So there is a new function to be implemented in the interpreter:

```scala
override def comingBackMode(request: Stream[F, Location]): Stream[F, ComingBackModeResponse] =
  for {
    _        <- Stream.eval(Logger[F].info(s"$serviceName - Enabling Coming Back Home mode"))
    location <- request
    _ <- Stream.eval(
      if (location.distanceToDestination > 0.0d) Logger[F].info(s"$serviceName - Distance to destination: ${location.distanceToDestination} mi")
      else Logger[F].info(s"$serviceName - You have reached your destination 🏡"))
    response <- Stream.eval(SmartHomeSupervisor[F].performAction(location))
  } yield response
```

### Client

Again, if the client will emit a stream of locations, we should develop a producer, which as been created at `LocationGenerators`. No big deal, so far. But we have to add this operation in the `SmartHomeServiceApi`:

```scala
trait SmartHomeServiceApi[F[_]] {
  def isEmpty(): F[Boolean]
  def getTemperature(): Stream[F, Temperature]
  def comingBackMode(locations: Stream[F, Location]): F[Boolean]
}
```

Whose interpretation could be:

```scala
def comingBackMode(locations: Stream[F, Location]): Stream[F, ComingBackModeResponse] = for {
  client   <- Stream.eval(clientF)
  response <- client.comingBackMode(locations)
} yield response
```

Now, we have all the ingredients to proceed in the ClientApp:

```scala
for {
  serviceApi <- SmartHomeServiceApi.createInstance(config.host.value, config.port.value)
  _          <- Stream.eval(serviceApi.isEmpty)
  summary    <- serviceApi.getTemperature
  _          <- Stream.eval(Logger[F].info(s"The average temperature is: ${summary.averageTemperature}"))
  response   <- serviceApi.comingBackMode(LocationsGenerator.get[F])
} yield response.actions
```

### Result

When we run the client now with `sbt runClient` we get:

```bash
INFO  - 👀 - Waiting for a new location...

INFO  - 👀 - Waiting for a new location...

INFO  - 👀 - Waiting for a new location...

INFO  - 👀 - Waiting for a new location...

INFO  - 👮 - Enable security cameras

INFO  - 👀 - Waiting for a new location...

INFO  - 👀 - Waiting for a new location...

INFO  - 👀 - Waiting for a new location...

INFO  - 👀 - Waiting for a new location...

INFO  - 👀 - Waiting for a new location...

INFO  - 👀 - Waiting for a new location...

INFO  - 💦 - Disable irrigation system

INFO  - 🔌 - Send Rumba to the charging dock

INFO  - 🛋 - Start heating the living room

INFO  - 👀 - Waiting for a new location...

INFO  - 👀 - Waiting for a new location...

INFO  - 👀 - Waiting for a new location...

INFO  - 👀 - Waiting for a new location...

INFO  - 👀 - Waiting for a new location...

INFO  - 🔥 - Fireplace in ambient mode

INFO  - 🗞 - Get news summary

INFO  - 👀 - Waiting for a new location...

INFO  - 👀 - Waiting for a new location...

INFO  - 👀 - Waiting for a new location...

INFO  - 👀 - Waiting for a new location...

INFO  - 👀 - Waiting for a new location...

INFO  - 👀 - Waiting for a new location...

INFO  - 💧 - Increase the power of the hot water heater

INFO  - 🛁 - Turn the towel heaters on

INFO  - 👀 - Waiting for a new location...

INFO  - 👀 - Waiting for a new location...

INFO  - 👀 - Waiting for a new location...

INFO  - 👀 - Waiting for a new location...

INFO  - 👀 - Waiting for a new location...

INFO  - 👀 - Waiting for a new location...

INFO  - 😎 - Low the blinds

INFO  - 💡 - Turn on the lights

INFO  - 👀 - Waiting for a new location...

INFO  - 👀 - Waiting for a new location...

INFO  - 👀 - Waiting for a new location...

INFO  - 👀 - Waiting for a new location...

INFO  - 👀 - Waiting for a new location...

INFO  - 👀 - Waiting for a new location...

INFO  - 👩 - Connect Alexa

INFO  - 📺 - Turn on the TV

INFO  - 👀 - Waiting for a new location...

INFO  - 👀 - Waiting for a new location...

INFO  - 👀 - Waiting for a new location...

INFO  - 👀 - Waiting for a new location...

INFO  - 👀 - Waiting for a new location...

INFO  - 👀 - Waiting for a new location...

INFO  - 🔦 - Turn exterior lights on

INFO  - 👀 - Waiting for a new location...

INFO  - 👀 - Waiting for a new location...

INFO  - 👀 - Waiting for a new location...

INFO  - 🚪 - Unlock doors

INFO  - Removed 1 RPC clients from cache.
```

And the server log the request as expected:

```bash
INFO  - SmartHomeService - Enabling Coming Back Home mode
INFO  - SmartHomeService - Distance to destination: 6.39 mi
INFO  - SmartHomeService - Distance to destination: 6.26 mi
INFO  - SmartHomeService - Distance to destination: 6.13 mi
INFO  - SmartHomeService - Distance to destination: 6.0 mi
INFO  - SmartHomeService - Distance to destination: 5.87 mi
INFO  - SmartHomeService - Distance to destination: 5.74 mi
INFO  - SmartHomeService - Distance to destination: 5.61 mi
INFO  - SmartHomeService - Distance to destination: 5.48 mi
INFO  - SmartHomeService - Distance to destination: 5.35 mi
INFO  - SmartHomeService - Distance to destination: 5.22 mi
INFO  - SmartHomeService - Distance to destination: 5.09 mi
INFO  - SmartHomeService - Distance to destination: 4.96 mi
INFO  - SmartHomeService - Distance to destination: 4.83 mi
INFO  - SmartHomeService - Distance to destination: 4.7 mi
INFO  - SmartHomeService - Distance to destination: 4.57 mi
INFO  - SmartHomeService - Distance to destination: 4.44 mi
INFO  - SmartHomeService - Distance to destination: 4.31 mi
INFO  - SmartHomeService - Distance to destination: 4.18 mi
INFO  - SmartHomeService - Distance to destination: 4.05 mi
INFO  - SmartHomeService - Distance to destination: 3.91 mi
INFO  - SmartHomeService - Distance to destination: 3.78 mi
INFO  - SmartHomeService - Distance to destination: 3.65 mi
INFO  - SmartHomeService - Distance to destination: 3.52 mi
INFO  - SmartHomeService - Distance to destination: 3.39 mi
INFO  - SmartHomeService - Distance to destination: 3.26 mi
INFO  - SmartHomeService - Distance to destination: 3.13 mi
INFO  - SmartHomeService - Distance to destination: 3.0 mi
INFO  - SmartHomeService - Distance to destination: 2.87 mi
INFO  - SmartHomeService - Distance to destination: 2.74 mi
INFO  - SmartHomeService - Distance to destination: 2.61 mi
INFO  - SmartHomeService - Distance to destination: 2.48 mi
INFO  - SmartHomeService - Distance to destination: 2.35 mi
INFO  - SmartHomeService - Distance to destination: 2.22 mi
INFO  - SmartHomeService - Distance to destination: 2.09 mi
INFO  - SmartHomeService - Distance to destination: 1.96 mi
INFO  - SmartHomeService - Distance to destination: 1.83 mi
INFO  - SmartHomeService - Distance to destination: 1.7 mi
INFO  - SmartHomeService - Distance to destination: 1.57 mi
INFO  - SmartHomeService - Distance to destination: 1.44 mi
INFO  - SmartHomeService - Distance to destination: 1.3 mi
INFO  - SmartHomeService - Distance to destination: 1.17 mi
INFO  - SmartHomeService - Distance to destination: 1.04 mi
INFO  - SmartHomeService - Distance to destination: 0.91 mi
INFO  - SmartHomeService - Distance to destination: 0.78 mi
INFO  - SmartHomeService - Distance to destination: 0.65 mi
INFO  - SmartHomeService - Distance to destination: 0.52 mi
INFO  - SmartHomeService - Distance to destination: 0.39 mi
INFO  - SmartHomeService - Distance to destination: 0.26 mi
INFO  - SmartHomeService - Distance to destination: 0.13 mi
INFO  - SmartHomeService - You have reached your destination 🏡
```

<!-- DOCTOC SKIP -->
# Copyright

Freestyle-RPC is designed and developed by 47 Degrees

Copyright (C) 2017 47 Degrees. <http://47deg.com>

[comment]: # (End Copyright)