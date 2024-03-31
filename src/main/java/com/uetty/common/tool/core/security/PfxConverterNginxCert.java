package com.uetty.common.tool.core.security;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.Base64;
import java.util.Scanner;
import java.util.UUID;

public class PfxConverterNginxCert {

    private static String getFileNamePrefix(String fileName) {
        File file = new File(fileName);
        String name = file.getName();
        if (name.endsWith(".pfx") || name.endsWith(".p12")) {
            name = name.substring(0, name.length() - 4);
        }
        return name;
    }

    public static void main(String[] args) throws Exception {
        String password;
        String pfxFilePath;
        if (args.length < 2) {
            System.out.println("Usage: java -jar pfx-converter.jar <password> <pfx_file_path>");
            System.out.println();
            Scanner scanner = new Scanner(System.in);
            System.out.print("Password: ");
            password = scanner.next().trim();
            System.out.print("Input file: ");
            pfxFilePath = scanner.next().trim();
        } else {
            password = args[0];
            pfxFilePath = args[1];
            System.out.println("Password: " + password);
            System.out.println("Input file: " + pfxFilePath);
        }

        String fileNamePrefix = getFileNamePrefix(pfxFilePath);
        String tempDir = System.getProperty("java.io.tmpdir");
        String outputFolder = UUID.randomUUID().toString();
        File outputFile = new File(tempDir, outputFolder);
        if (!outputFile.exists()) {
            outputFile.mkdirs();
//            outputFile.deleteOnExit();
        }

        String keyFileName = fileNamePrefix + ".key";
        String certFileName = fileNamePrefix + ".pem";
        String combinedFileName = fileNamePrefix + ".pem";
        File pfxFile = new File(pfxFilePath);
        File keyFile = new File(outputFile, keyFileName);
        File certFile = new File(outputFile, certFileName);

        // Load the PFX file
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(new FileInputStream(pfxFile), password.toCharArray());

        // Get the private key and certificate chain from the keystore
        String alias = keyStore.aliases().nextElement(); // Assuming there is only one entry in the keystore
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, password.toCharArray());
        Certificate[] chain = keyStore.getCertificateChain(alias);

        // Write the private key to a .key file
        try (FileOutputStream keyOutputStream = new FileOutputStream(keyFile)) {
            keyOutputStream.write("-----BEGIN PRIVATE KEY-----\n".getBytes());
            keyOutputStream.write(Base64.getEncoder().encode(privateKey.getEncoded()));
            keyOutputStream.write("\n-----END PRIVATE KEY-----\n".getBytes());
        }

        // Write the certificate chain to a .pem file
        try (FileOutputStream pemOutputStream = new FileOutputStream(certFile)) {
            for (Certificate cert : chain) {
                pemOutputStream.write("-----BEGIN CERTIFICATE-----\n".getBytes());
                pemOutputStream.write(Base64.getEncoder().encode(cert.getEncoded()));
                pemOutputStream.write("\n-----END CERTIFICATE-----\n".getBytes());
            }
        }

        System.out.println("Conversion completed successfully.");
        System.out.println("Key file: " + keyFile.getAbsolutePath());
    }

}
