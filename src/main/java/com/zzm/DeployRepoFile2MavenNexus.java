package com.zzm;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.commons.lang3.StringUtils;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 批量上传本地maven库（文件）依赖到 Maven 私服
 *
 * @author
 * @since
 */
public class DeployRepoFile2MavenNexus {

  public static final Pattern DATE_PATTERN = Pattern.compile("-[\\d]{8}\\.[\\d]{6}-");
  public static final Runtime CMD = Runtime.getRuntime();
  public static final Writer ERROR;
  public static String BASE_CMD =
      "cmd /c mvn -s %s deploy:deploy-file \n" + " -Durl=%s -DrepositoryId=%s -DgeneratePom=false";
  public static String repoDir;

  /**
   * 初始化线程池
   */
  public static ExecutorService EXECUTOR_SERVICE =
      new ThreadPoolExecutor(1, 2, 0L, TimeUnit.MILLISECONDS,
          new LinkedBlockingQueue<Runnable>(1024),
          new ThreadFactoryBuilder().setNameFormat("thread-pool-%d").build());

  static {
    Writer err = null;
    try {
      err = new OutputStreamWriter(new FileOutputStream("deploy-error.log"), StandardCharsets.UTF_8);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(0);
    }
    ERROR = err;
    try (InputStream in = ClassLoader.class.getResourceAsStream("/maven_config.properties")) {
      Properties pro = new Properties();
      pro.load(in);
      repoDir = pro.getProperty("local.mvn.repo.or.sub.dir");
      String settingsXml = pro.getProperty("local.mvn.settings.xml.location");
      String repoUrl = pro.getProperty("nexus.maven.repo.url");
      String repoId = pro.getProperty("nexus.maven.repo.id");
      
      System.out.println("local.mvn.repo.or.sub.dir:"+repoDir);
      System.out.println("local.mvn.settings.xml.location:"+settingsXml);
      System.out.println("nexus.maven.repo.url:"+repoUrl);
      System.out.println("nexus.maven.repo.id:"+repoId);
      if (checkPropertiesExistNull(repoDir,repoUrl,repoId,settingsXml)){
        error("there exists one property that is empty!");
        System.exit(0);
      }
      System.out.println("config settingsXml path:" + settingsXml);
      File settingsXmlFile = new File(settingsXml);
      if (settingsXmlFile.exists() && settingsXmlFile.isFile()) {
        //读取settings.xml文件流
        SAXReader reader = new SAXReader();
        //获得文件实例
        Document document = reader.read(settingsXmlFile);
        Element localRepositoryEle = document.getRootElement().element("localRepository");
        if (localRepositoryEle != null) {
          System.out.println("settings.xml localRepository:" + localRepositoryEle.getText());
          String localRepositoryValue = localRepositoryEle.getText();
          if (localRepositoryValue.equalsIgnoreCase(repoDir) || localRepositoryValue
              .equalsIgnoreCase(repoDir + "\\") || localRepositoryValue
              .equalsIgnoreCase(repoDir + "/")) {
            error("local.mvn.settings.xml.location的值不能是" + repoDir);
            System.exit(0);
          }
        }
      } else {
        error(settingsXml + " does not exist or it is not a file directory!");
      }
      BASE_CMD = String.format(BASE_CMD, settingsXml, repoUrl, repoId);
      System.out.println(BASE_CMD);
    } catch (IOException e) {
      System.exit(0);
    } catch (DocumentException e) {
      System.exit(0);
    }
  }

  private static boolean checkPropertiesExistNull(String... properties) {
    if (properties.length > 0) {
      for (String pro : properties){
        if (StringUtils.isBlank(pro)){
          return true;
        }
      }
    }
    return false;
  }

  public static void main(String[] args) {
    //deploy(new File("G:\\dev\\maven_repository_zzm").listFiles());
    long startTime = System.currentTimeMillis();
    deploy(new File(repoDir).listFiles());
    /*if(checkArgs(args)){
      File file = new File(args[0]);
      deploy(file.listFiles());
    }*/
    EXECUTOR_SERVICE.shutdown();
    try {
      ERROR.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    long endTime = System.currentTimeMillis();
    System.out.println("耗时："+(endTime-startTime)+"ms");
  }

  public static void error(String error) {
    try {
      System.err.println(error);
      ERROR.write(error + "\n");
      ERROR.flush();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static boolean checkArgs(String[] args) {
    if (args.length != 1) {
      System.out.println("Usages:: java -jar Deploy D:\\some\\path\\");
      return false;
    }
    File file = new File(args[0]);
    if (!file.exists()) {
      System.out.println(args[0] + " file directory does not exists!");
      return false;
    }
    if (!file.isDirectory()) {
      System.out.println("must be file directory !");
      return false;
    }
    return true;
  }

  public static void deploy(File[] files) {
    if (files.length == 0) {
      //ignore
    } else if (files[0].isDirectory()) {
      for (File file : files) {
        if (file.isDirectory()) {
          deploy(file.listFiles());
        }
      }
    } else if (files[0].isFile()) {
      File pom = null;
      File jar = null;
      File source = null;
      File javadoc = null;
      //忽略日期快照版本，如 xxx-mySql-2.2.6-20170714.095105-1.jar
      for (File file : files) {
        String name = file.getName();
        if (DATE_PATTERN.matcher(name).find()) {
          //skip
        } else if (name.endsWith(".pom")) {
          pom = file;
        } else if (name.endsWith("-javadoc.jar")) {
          javadoc = file;
        } else if (name.endsWith("-sources.jar")) {
          source = file;
        } else if (name.endsWith(".jar")) {
          jar = file;
        }
      }
      if (pom != null) {
        if (jar != null) {
          deploy(pom, jar, source, javadoc);
        } else if (packingIsPom(pom)) {
          deployPom(pom);
        }
      }
    }
  }

  public static boolean packingIsPom(File pom) {
    BufferedReader reader = null;
    try {
      reader = new BufferedReader(new InputStreamReader(new FileInputStream(pom)));
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.trim().indexOf("<packaging>pom</packaging>") != -1) {
          return true;
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      try {
        reader.close();
      } catch (Exception e) {
      }
    }
    return false;
  }

  public static void deployPom(final File pom) {
    //EXECUTOR_SERVICE.execute(new Runnable() {
    //  @Override public void run() {
    StringBuffer cmd = new StringBuffer(BASE_CMD);
    cmd.append(" -DpomFile=").append(pom.getName());
    cmd.append(" -Dfile=").append(pom.getName());
    try {
      final Process proc = CMD.exec(cmd.toString(), null, pom.getParentFile());
      InputStream inputStream = proc.getInputStream();
      InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
      BufferedReader reader = new BufferedReader(inputStreamReader);
      String line;
      StringBuffer logBuffer = new StringBuffer();
      logBuffer.append("\n\n\n==================================\n");
      while ((line = reader.readLine()) != null) {
        if (line.startsWith("[INFO]") || line.startsWith("Upload")) {
          logBuffer.append(Thread.currentThread().getName() + " : " + line + "\n");
        }
      }
      System.out.println(logBuffer);
      int result = proc.waitFor();
      if (result != 0) {
        error("Deploy failed:" + pom.getAbsolutePath());
      }
    } catch (IOException e) {
      error("Deploy failed:" + pom.getAbsolutePath());
      e.printStackTrace();
    } catch (InterruptedException e) {
      error("Deploy failed:" + pom.getAbsolutePath());
      e.printStackTrace();
    }
    //}
    //});
  }

  public static void deploy(final File pom, final File jar, final File source, final File javadoc) {
    //EXECUTOR_SERVICE.execute(new Runnable() {
    //  @Override public void run() {
    StringBuffer cmd = new StringBuffer(BASE_CMD);
    cmd.append(" -DpomFile=").append(pom.getName());
    if (jar != null) {
      //当有bundle类型时，下面的配置可以保证上传的jar包后缀为.jar
      cmd.append(" -Dpackaging=jar -Dfile=").append(jar.getName());
    } else {
      cmd.append(" -Dfile=").append(pom.getName());
    }
    if (source != null) {
      cmd.append(" -Dsources=").append(source.getName());
    }
    if (javadoc != null) {
      cmd.append(" -Djavadoc=").append(javadoc.getName());
    }
    try {
      final Process proc = CMD.exec(cmd.toString(), null, pom.getParentFile());
      InputStream inputStream = proc.getInputStream();
      InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
      BufferedReader reader = new BufferedReader(inputStreamReader);
      String line;
      StringBuffer logBuffer = new StringBuffer();
      logBuffer.append("\n=======================================\n");
      while ((line = reader.readLine()) != null) {
        if (line.startsWith("[INFO]") || line.startsWith("Upload")) {
          logBuffer.append(Thread.currentThread().getName() + " : " + line + "\n");
        }
      }
      System.out.println(logBuffer);
      int result = proc.waitFor();
      if (result != 0) {
        error("Deploy failed:" + pom.getAbsolutePath());
      }
    } catch (IOException e) {
      error("Deploy failed:" + pom.getAbsolutePath());
      e.printStackTrace();
    } catch (InterruptedException e) {
      error("Deploy failed:" + pom.getAbsolutePath());
      e.printStackTrace();
    }
    //}
    //});
  }
}
