package eu.inn.forgame.statusmonitor

import eu.inn.util.Logging


object MainApp extends App with ComponentRegistry with Logging {

  log.info("Start Status Monitor Service...")

  println(statusMonitorService.toString())
}
