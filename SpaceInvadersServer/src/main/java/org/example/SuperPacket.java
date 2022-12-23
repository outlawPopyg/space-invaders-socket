package org.example;

// Что нужно добавить в ваш протокол:
//  Поддержку типов и подтипов пакетов.
//  Три типа и подтипа пакета: стандартный, goodbye и handshake.
//    ТИП meta:
// todo
//     ПОДТИП handshake пакет служит для проверки, что клиент и сервер работают по одному протоколу
//     ПОДТИП goodbye пакет служит для прекращения соединения
//     ТИП стандартный пакет содержит данные в формате json
//     ТИП стандартный ПОДТИП защищенный - содержит данные зашифрованые по ключу вашим любимым протоколом шифрования (сервер и клиент должны задавать ключ при старте общения отдельным пакетом)

import lombok.Data;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

@Data
public class SuperPacket {
    private static final byte HEADER_1 = (byte) 0xe; // 14
    private static final byte HEADER_2 = (byte) 0x58; // 88
    private static final byte HEADER_3 = (byte) 0x0; // 0

    private static final byte FOOTER_1 = (byte) 0x1b; // 27
    private static final byte FOOTER_2 = (byte) 0x36; // 54
    private static final byte FOOTER_3 = (byte) 0x63; // 99

    private static final byte SEPARATOR_1 = (byte)  0x45; // 69
    private static final byte SEPARATOR_2 = (byte) 0x60; // 96

    private static final byte SEPARATOR_3 = (byte) 0x2c; // 44
    private static final byte SEPARATOR_4 = (byte) 0x2c; // 44

    private byte type;

    private byte[] key;

    private List<SuperField> superFields = new LinkedList<>();

    private SuperPacket() {}

    public static SuperPacket create(int type) {
        SuperPacket packet = new SuperPacket();
        packet.type = (byte) type;
        return packet;
    }

    public static SuperPacket create(String key) {
        SuperPacket packet = new SuperPacket();
        packet.type = (byte) 3;
        packet.key = key.getBytes();
        return packet;
    }

    public static boolean compareEndOfPacket(byte[] bArr, int lastItem) {
        return bArr[lastItem - 2] == FOOTER_1 && bArr[lastItem - 1] == FOOTER_2 && bArr[lastItem] == FOOTER_3;
    }

    public byte[] toByteArray() {
        try (ByteArrayOutputStream writer = new ByteArrayOutputStream()) {
            writer.write(new byte[] {HEADER_1, HEADER_2, HEADER_3});
            writer.write(type);

            for (SuperField field : superFields) {
                writer.write(new byte[] {field.getId(), field.getSize()});
                writer.write(field.getContent());


                if (field.getAClass() != null) {
                    writer.write(SEPARATOR_1);
                    writer.write(SEPARATOR_2);

                    writer.write(field.getAClassSize());
                    writer.write(field.getAClass());
                }
            }

            if (type == 3) {
                writer.write(SEPARATOR_3);
                writer.write(SEPARATOR_4);

                writer.write((byte) key.length);
                writer.write(key);
            }

            writer.write(new byte[] {FOOTER_1, FOOTER_2, FOOTER_3});

            return writer.toByteArray();

        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static SuperPacket parse(byte[] data) {
        if (data[0] != HEADER_1 && data[1] != HEADER_2 && data[2] != HEADER_3 ||
            data[data.length - 1] != FOOTER_3 && data[data.length - 2] != FOOTER_2 && data[data.length - 3] != FOOTER_1) {

            throw new IllegalArgumentException("Can't define SuperPacket");
        }

        byte type = data[3];
        SuperPacket packet = SuperPacket.create(type);
        int offset = 4;
        while (true) {
            if (data.length - 3 <= offset) {
                return packet;
            }

            byte fieldID = data[offset];
            byte fieldSize = data[offset + 1];
            byte[] content = new byte[Byte.toUnsignedInt(fieldSize)];

            if (fieldSize != 0) {
                System.arraycopy(data, offset + 2, content, 0, Byte.toUnsignedInt(fieldSize));
            }

            SuperField field = new SuperField(fieldID, fieldSize, content);
            packet.getSuperFields().add(field);

            offset += 2 + fieldSize;

            if (data[offset] == SEPARATOR_1 && data[offset + 1] == SEPARATOR_2) {
                byte aClassSize = data[offset + 2];
                byte[] aClass = new byte[Byte.toUnsignedInt(aClassSize)];

                if (aClassSize != 0) {
                    System.arraycopy(data, offset + 3, aClass, 0, aClassSize);
                }

                field.setaClassSize(aClassSize);
                field.setaClass(aClass);

                offset += 3 + aClassSize;
            }

            if (type == 3 && data[offset] == SEPARATOR_3 && data[offset + 1] == SEPARATOR_4) {
                byte keyLength = data[offset + 2];
                byte[] key = new byte[Byte.toUnsignedInt(keyLength)];
                System.arraycopy(data, offset + 3, key, 0, keyLength);

                packet.key = key;

                offset += 3 + keyLength;
            }
        }
    }

    public SuperField getField(int id) {
        Optional<SuperField> filed = getSuperFields().stream()
                .filter(superField -> superField.getId() == (byte) id)
                .findFirst();

        if (filed.isEmpty()) {
            throw new IllegalArgumentException("No such field");
        }

        return filed.get();
    }

    public <T> T getValue(int id, Class<T> tClass) {
        SuperField field = getField(id);
        byte[] content = field.getContent();

        if (type == 3) {
            try {
                Cipher cipher = Cipher.getInstance("AES");

                String secretKey = new String(key);

                SecretKeySpec key = new SecretKeySpec(secretKey.getBytes(), "AES");
                cipher.init(Cipher.DECRYPT_MODE, key);

                content = cipher.doFinal(content);


            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
        }

        try (ByteArrayInputStream bis = new ByteArrayInputStream(content);
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            return (T) ois.readObject();

        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public void setValue(int id, Object value) {
        SuperField field;
        boolean isAlreadyExists = false;

        try {
            field = getField(id);
            isAlreadyExists = true;
        } catch (IllegalArgumentException e) {
            field = new SuperField((byte) id);
        }

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {

            oos.writeObject(value);
            byte[] content = bos.toByteArray();

            if (type == 3) {
                try {
                    Cipher cipher = Cipher.getInstance("AES");

                    String secretKey = new String(key);

                    SecretKeySpec key = new SecretKeySpec(secretKey.getBytes(), "AES");
                    cipher.init(Cipher.ENCRYPT_MODE, key);

                    content = cipher.doFinal(content);


                } catch (Exception e) {
                    throw new IllegalArgumentException(e);
                }
            }

            if (content.length > 255) {
                throw new IllegalArgumentException("Too big data");
            }

            field.setSize((byte) content.length);
            field.setContent(content);

        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }

        if (!isAlreadyExists) {
            getSuperFields().add(field);
        }
    }

    @Data
    public static class SuperField {
        private byte id;
        private byte size;
        private byte[] content;
        private byte aClassSize;
        private byte[] aClass;

        public SuperField(byte id, byte size, byte[] content) {
            this.id = id;
            this.size = size;
            this.content = content;
        }

        public void setaClass(byte[] aClass) {
            this.aClass = aClass;
        }

        public void setaClassSize(byte aClassSize) {
            this.aClassSize = aClassSize;
        }

        public SuperField(byte id) {
            this.id = id;
        }
    }
}
