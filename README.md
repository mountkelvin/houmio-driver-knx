# Installing Java 8 on Raspberry Pi

Download JDK from http://www.oracle.com/technetwork/java/javase/downloads/jdk8-arm-downloads-2187472.html

    sudo tar zxvf jdk-8u6-linux-arm-vfp-hflt.tar.gz -C /opt
    sudo update-alternatives --install /usr/bin/javac javac /opt/jdk1.8.0_06/bin/javac 1
    sudo update-alternatives --install /usr/bin/java java /opt/jdk1.8.0_06/bin/java 1
    sudo update-alternatives --config javac
    sudo update-alternatives --config java

Verify:

    java -version
    javac -version

# Installing JDK 8 on your PC (OS X)

http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html

Maven requires $JAVA_HOME to be set correctly  e.g.

    cd ~
    cat .bash_profile
    export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_25.jdk/Contents/Home


# Building the uberjar

  mvn clean install

The jar is in `target` dir.
