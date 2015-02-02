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

# Running the driver with Houm.io

    cd ~
    git clone https://github.com/houmio/houmio-driver-knx

Add `HOUMIO_KNX_IP="aaa.bbb.ccc.ddd"` to `~/houmio.conf` `environment`.

Add `HOUMIO_NETWORK_INTERFACE=putyourinterfacehere` to `~/houmio.conf` `environment`, if the host has multiple network interfaces.

# Building and running from the command line

Set env variable `HOUMIO_KNX_IP` to point to your KNX IP gateway.

Set env variable `HOUMIO_NETWORK_INTERFACE` if the host has multiple network interfaces.

Then:

    mvn clean install
    java -jar target/houmio-driver-knx-0.0.1-jar-with-dependencies.jar
