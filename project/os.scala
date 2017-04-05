package org.scalameta
package os

import java.io._
import scala.sys.process._
import scala.compat.Platform.EOL

object time {
  // cached here to use consistent time stamp across sbt reloads, which happen
  // for every ++SCALA_VERSION switch in sbt "very publish"
  val stamp = System.currentTimeMillis().toString
}

object shell {
  def exec(command: String, cwd: String = "."): (Int, String, String) = {
    def slurp(stream: InputStream): String = {
      val reader = new BufferedReader(new InputStreamReader(stream))
      val builder = new StringBuilder()
      var done = false
      while (!done) {
        val line = reader.readLine()
        if (line != null) builder.append(line + EOL)
        else done = true
      }
      builder.toString
    }
    val p = Runtime.getRuntime.exec(command, null, new File(cwd))
    val exitcode = p.waitFor()
    val stdout = slurp(p.getInputStream) // lol at the naming
    val stderr = slurp(p.getErrorStream)
    (exitcode, stdout, stderr)
  }

  def call(command: String, cwd: String = "."): Unit = {
    val sfwCommand = command.replaceAll("https://(.*?):(.*)@", "https://***:***@");
    println("running " + sfwCommand + " in " + new File(cwd).getAbsolutePath)
    val (exitcode, stdout, stderr) = shell.exec(command, cwd)
    if (exitcode != 0) sys.error(s"$command returned $exitcode:$EOL$stdout$EOL$stderr")
  }

  def check_output(command: String, cwd: String = "."): String = {
    val sfwCommand = command.replaceAll("https://(.*?):(.*)@", "https://***:***@");
    println("running " + sfwCommand + " in " + new File(cwd).getAbsolutePath)
    val (exitcode, stdout, stderr) = shell.exec(command, cwd)
    if (exitcode != 0) sys.error(s"$command returned $exitcode:$EOL$stdout$EOL$stderr")
    stdout
  }
}

object secret {
  def obtain(domain: String): Option[(String, String)] = {
    val credentialsFile = System.getProperty(domain + ".settings.file")
    if (credentialsFile != null) {
      try {
        import scala.xml._
        val settings = XML.loadFile(credentialsFile)
        def readServerConfig(key: String) =
          (settings \\ "settings" \\ "servers" \\ "server" \\ key).head.text
        Some((readServerConfig("username"), readServerConfig("password")))
      } catch {
        case ex: Exception => None
      }
    } else {
      for {
        username <- sys.env.get(s"${domain.toUpperCase}_USERNAME")
        password <- sys.env.get(s"${domain.toUpperCase}_PASSWORD")
      } yield {
        (username, password)
      }
    }
  }
}

object temp {
  def mkdir(): File = {
    val temp = File.createTempFile("temp", System.nanoTime.toString)
    if (!temp.delete)
      sys.error("failed to create a temporary directory: can't delete " + temp.getAbsolutePath)
    if (!temp.mkdir)
      sys.error("failed to create a temporary directory: can't mkdir " + temp.getAbsolutePath)
    temp
  }
}

object shutil {
  def rmtree(file: File): Unit = {
    if (file.isDirectory) file.listFiles.foreach(rmtree)
    if (!file.delete) sys.error(s"failed to delete ${file.getAbsolutePath}")
  }

  def copytree(src: File, dest: File): Unit = {
    if (src.isDirectory) {
      if (!dest.mkdirs) sys.error(s"failed to create ${dest.getAbsolutePath}")
      src.listFiles.foreach(srcsub => {
        val destsub = new File(dest.getAbsolutePath + File.separator + srcsub.getName)
        copytree(srcsub, destsub)
      })
    } else {
      val in = new FileInputStream(src)
      try {
        val out = new FileOutputStream(dest)
        try {
          val buf = new Array[Byte](1024)
          var done = false
          while (!done) {
            val len = in.read(buf)
            if (len > 0) out.write(buf, 0, len)
            else done = true
          }
        } finally {
          out.close()
        }
      } finally {
        in.close();
      }
    }
  }
}

object git {
  def isStable(): Boolean = {
    def noUntrackedFiles = {
      val untracked = shell.check_output("git ls-files --others --exclude-standard", cwd = ".")
      untracked.trim.isEmpty
    }
    def noModifiedFiles = {
      val changed = shell.check_output("git diff --name-status", cwd = ".")
      changed.trim.isEmpty
    }
    def noStagedFiles = {
      val staged = shell.check_output("git diff --staged --name-status", cwd = ".")
      staged.trim.isEmpty
    }
    noUntrackedFiles && noModifiedFiles && noStagedFiles
  }

  def distance(from: String, to: String): Int = {
    def ncommits(ref: String) =
      shell.check_output(s"git rev-list $ref --count", cwd = ".").trim.toInt
    ncommits(to) - ncommits(from)
  }

  def currentSha(): String = {
    shell.check_output("git rev-parse HEAD", cwd = ".").trim
  }
}

object version {
  def stable(): String = {
    // TODO: uncomment this once we release 3.0.0
    // val stdout = shell.check_output(s"git tag -l v*")
    // val latestTag = stdout.split(EOL).last
    // val status = """^v(\d+)\.(\d+)\.(\d+)$""".r.unapplySeq(latestTag)
    // if (status.isEmpty) sys.error(s"unexpected shape of tag $latestTag in$EOL$stdout")
    // latestTag.stripPrefix("v")
    ???
  }

  def preRelease(): String = {
    val nextStableVersion = {
      // TODO: uncomment this once we release 3.0.0
      // val currStableVersion = stable()
      // val kindaSemVer = """^(\d+)\.(\d+)\.(\d+)$""".r
      // currStableVersion match {
      //   case kindaSemVer(s_currEpoch, s_currMajor, _) =>
      //     val currMajor = {
      //       try s_currMajor.toInt
      //       catch { case ex: Exception => sys.error(s"unexpected shape of version.stable() in $currStableVersion") }
      //     }
      //     val nextMajor = currMajor + 1
      //     s"$s_currEpoch.$nextMajor.0"
      // }
      "3.0.0"
    }
    val preReleaseSuffix = {
      val gitDescribeSuffix = {
        // TODO: uncomment this once we release 3.0.0
        // val distance = os.git.distance("v3.0.0", "HEAD")
        val distance = os.git.distance("v3.0.0-M1_2.11.8", "HEAD")
        val currentSha = os.git.currentSha().substring(0, 8)
        s"$distance-$currentSha"
      }
      if (os.git.isStable()) gitDescribeSuffix
      else gitDescribeSuffix + "." + os.time.stamp
    }
    nextStableVersion + "-" + preReleaseSuffix
  }
}
