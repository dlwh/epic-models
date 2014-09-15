/*
 *
 *  Copyright 2014 David Hall
 *
 *  Licensed under the Apache License, Version 2.0 (the "License")
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * /
 */

import java.io._
import java.util.zip.GZIPInputStream

import breeze.util.SerializableLogging

/**
 * Class that reads in objects serialized with [[breeze.util.writeObject]], ignoring their serialversionuids,
 * and then writes them to the same file.
 *
 * @author dlwh
 */
object UpdateSerializedObjects {

  def main(args: Array[String]): Unit = {
    for(a <- args) {
      breeze.util.writeObject[AnyRef](new File(a), readObject(new File(a), ignoreSerialVersionUID = true))
    }
  }

  /**
   * Deserializes an object using java serialization
   */
  def readObject[T](loc: File, ignoreSerialVersionUID: Boolean) = {
    val stream = new BufferedInputStream(new GZIPInputStream(new FileInputStream(loc)))
    val oin = nonstupidObjectInputStream(stream, ignoreSerialVersionUID)
    try {
      oin.readObject().asInstanceOf[T]
    } finally {
      oin.close()
    }
  }
  /**
   * For reasons that are best described as asinine, ObjectInputStream does not take into account
   * Thread.currentThread.getContextClassLoader. This fixes that.
   *
   * @param stream
   * @param ignoreSerialVersionUID this is not a safe thing to do, but sometimes...
   * @return
   */
  def nonstupidObjectInputStream(stream: InputStream, ignoreSerialVersionUID: Boolean = false):ObjectInputStream =  {
    new ObjectInputStream(stream) with SerializableLogging {
      @throws[IOException]
      @throws[ClassNotFoundException]
      override def resolveClass(desc: ObjectStreamClass): Class[_] = {
        try {
          val currentTccl: ClassLoader = Thread.currentThread.getContextClassLoader
          currentTccl.loadClass(desc.getName)
        } catch {
          case e: Exception =>
            super.resolveClass(desc)
        }
      }


      // from http://stackoverflow.com/questions/1816559/make-java-runtime-ignore-serialversionuids
      override protected def readClassDescriptor(): ObjectStreamClass = {
        var resultClassDescriptor = super.readClassDescriptor(); // initially streams descriptor
        if(ignoreSerialVersionUID) {

          var localClass: Class[_] = null; // the class in the local JVM that this descriptor represents.
          try {
            localClass = Class.forName(resultClassDescriptor.getName)
          } catch {
            case e: ClassNotFoundException =>
              logger.error("No local class for " + resultClassDescriptor.getName, e)
              return resultClassDescriptor
          }

          val localClassDescriptor = ObjectStreamClass.lookup(localClass)
          if (localClassDescriptor != null) { // only if class implements serializable
          val localSUID = localClassDescriptor.getSerialVersionUID
            val streamSUID = resultClassDescriptor.getSerialVersionUID
            if (streamSUID != localSUID) { // check for serialVersionUID mismatch.
            val s = new StringBuffer("Overriding serialized class version mismatch: ")
              s.append("local serialVersionUID = ").append(localSUID)
              s.append(" stream serialVersionUID = ").append(streamSUID)
              val e = new InvalidClassException(s.toString())
              logger.error("Potentially Fatal Deserialization Operation.", e);
              resultClassDescriptor = localClassDescriptor; // Use local class descriptor for deserialization
            }

          }
        }
        resultClassDescriptor
      }
    }
  }

}
