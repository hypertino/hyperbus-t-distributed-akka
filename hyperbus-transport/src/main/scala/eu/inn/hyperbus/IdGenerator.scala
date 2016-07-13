package eu.inn.hyperbus

import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicInteger

object IdGenerator {
  private val random = new SecureRandom()
  private val base64t = "-0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz" // sorted by char code
  private val counter = new AtomicInteger(random.nextInt(65536))

  def create(): String = {
    val sb = new StringBuilder(30)
    appendInt(sb, (System.currentTimeMillis() / 10000l & 0xFFFFFFFFl).toInt)
    appendInt(sb, counter.incrementAndGet())
    appendInt(sb, random.nextInt())
    appendInt(sb, random.nextInt())
    appendInt(sb, random.nextInt())
    appendInt(sb, random.nextInt())
    sb.toString()
  }

  private def appendInt(sb: StringBuilder, i: Int): Unit = {
    sb.append(base64t.charAt(i >> 24 & 63))
    sb.append(base64t.charAt(i >> 18 & 63))
    sb.append(base64t.charAt(i >> 12 & 63))
    sb.append(base64t.charAt(i >> 6 & 63))
    sb.append(base64t.charAt(i & 63))
  }
}
