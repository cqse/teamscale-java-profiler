module com.teamscale.profiler.installer {
	requires okhttp3;
	requires info.picocli;
	requires com.sun.jna.platform;
	requires org.apache.commons.lang3;
	// adds support for elliptic curve SSL ciphers
	// used e.g. by teamscale.io
	requires jdk.crypto.ec;
	requires kotlin.stdlib;
}

