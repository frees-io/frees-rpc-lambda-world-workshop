import ProjectPlugin._

resolvers in ThisBuild += Resolver.bintrayRepo("beyondthelines", "maven")

lazy val protocol = project.in(file("protocol")).settings(rpcProtocolSettings)

lazy val commons = project.in(file("commons")).settings(commonsSettings)

lazy val client = project.in(file("client")).settings(rpcClientSettings).dependsOn(protocol, commons)

lazy val server = project.in(file("server")).settings(rpcServerSettings).dependsOn(protocol, commons)

lazy val allRootModules: Seq[ProjectReference] = Seq(commons, protocol, client, server)

lazy val allRootModulesDeps: Seq[ClasspathDependency] = allRootModules.map(ClasspathDependency(_, None))

lazy val root = project.in(file(".")).settings(name := "lwseattle-workshop").aggregate(allRootModules: _*).dependsOn(allRootModulesDeps: _*)

addCommandAlias("runServer", "server/runMain com.fortyseven.server.ServerApp")

addCommandAlias("runClient", "client/runMain com.fortyseven.client.ClientApp")
