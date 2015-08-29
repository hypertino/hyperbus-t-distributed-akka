package eu.inn.hyperbus.rest

import java.security.SecureRandom

object IdGenerator {
  private val random = new SecureRandom()
  private val base64t = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-~"

  def create(): String = {
    val sb = new StringBuilder(25)
    next(sb)
    next(sb)
    next(sb)
    next(sb)
    next(sb)
    sb.toString()
  }

  private def next(sb: StringBuilder): Unit = {
    val i = random.nextInt()
    sb.append(base64t.charAt(i & 63))
    sb.append(base64t.charAt(i >> 6 & 63))
    sb.append(base64t.charAt(i >> 12 & 63))
    sb.append(base64t.charAt(i >> 18 & 63))
    sb.append(base64t.charAt(i >> 24 & 63))
  }
}
