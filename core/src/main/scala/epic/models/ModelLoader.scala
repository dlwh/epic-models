package epic.models

import java.io.{BufferedInputStream, ObjectInputStream}
import java.util.zip.GZIPInputStream

/**
* TODO
*
* @author dlwh
**/
trait ModelLoader[+T] {
  def load():T
}

abstract class ClassPathModelLoader[T](modelPath: String = "model.ser.gz") extends ModelLoader[T] {
  def load() = {
    val input = this.getClass.getResourceAsStream("model.ser.gz")
    val gzipin = new ObjectInputStream(new GZIPInputStream(new BufferedInputStream(input)))
    try {
      gzipin.readObject().asInstanceOf[T]
    } finally {
      gzipin.close()
    }
  }
}
