../NetLogo/extensions/gis/sbt package && \
../NetLogo/extensions/gis/sbt publishLocal && \
cp target/scala-2.12/netlogo-extension-language-server-library_2.12-0.1-SNAPSHOT.jar ../NetLogo/extensions/py/ && \
cp target/scala-2.12/netlogo-extension-language-server-library_2.12-0.1-SNAPSHOT.jar ../NetLogoJS/js/ && \
cd ../NetLogo/extensions/py/ && \
sbt package
