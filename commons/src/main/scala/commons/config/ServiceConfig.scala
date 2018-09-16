package com.fortyseven.commons.config

case class Host(value: String) extends AnyVal

case class Port(value: Int) extends AnyVal

case class ServiceName(value: String) extends AnyVal

case class ServiceConfig(name: ServiceName, host: Host, port: Port)
