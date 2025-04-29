package com.bob.test;
import java.io.File;
import java.io.IOException;

public class FIlePermissionTest {

	public static void main(String[] args) throws IOException {
		File file = new File("test-fail.png");
		if(!file.exists()) {
			System.out.println("new file exist");
			System.out.println("file created "+file.createNewFile());
		}
		System.out.println(file.getAbsolutePath());
		file.setReadable(true, false);
		System.out.println(file.canRead());
		System.out.println(file.canWrite());

	}

}
