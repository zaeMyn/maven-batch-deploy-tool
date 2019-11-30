###部署命令
* mvn -s H:\zzm\.m2\settings.xml
* org.apache.maven.plugins:maven-deploy-plugin:2.8.2:deploy-file
* -Durl=http://IP:PORT/nexus/content/repositories/thirdpart
* -DrepositoryId=thirdpart
* -Dfile=antlr-2.7.2.jar
* -DpomFile=antlr-2.7.2.pom
* -Dpackaging=jar
* -DgeneratePom=false
* -Dsources=./path/to/artifact-name-1.0-sources.jar
* -Djavadoc=./path/to/artifact-name-1.0-javadoc.jar

##使用
######修改maven_config.properties文件。
######java -jar maven-batch-deploy-tool-1.0.jar

##要求(注意)
######1.settings.xml里面需要配置nexus的用户密码，以及资源库Id
######2.settings.xml里面[localRepository]不能与与maven_config.properties的待上传路径是同一个
######3.本地的settings.xml参考[settings-reference.xml](./settings-reference.xml)
######4.上传如果感觉慢的话，推荐用http://github.com/simpligility/maven-repository-tools
