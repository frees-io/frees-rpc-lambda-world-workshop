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

<!-- DOCTOC SKIP -->
# Copyright

Freestyle-RPC is designed and developed by 47 Degrees

Copyright (C) 2017 47 Degrees. <http://47deg.com>

[comment]: # (End Copyright)