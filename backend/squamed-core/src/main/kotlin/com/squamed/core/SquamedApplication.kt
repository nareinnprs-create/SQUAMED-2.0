package com.squamed.core

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class SquamedApplication

fun main(args: Array<String>) {
  runApplication<SquamedApplication>(*args)
}
