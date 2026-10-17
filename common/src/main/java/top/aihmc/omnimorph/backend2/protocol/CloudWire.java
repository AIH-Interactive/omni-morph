package top.aihmc.omnimorph.backend2.protocol;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.Descriptors;
import top.aihmc.omnimorph.backend2.protocol.generated.Cloud;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Checks nested/repeated budgets before generated parsers allocate object collections. */
public final class CloudWire {
    private CloudWire() {}

    public static Cloud.Envelope decode(byte[] bytes) throws IOException {
        if (bytes.length > 65536) {
            throw new IOException("Envelope exceeds byte budget");
        }
        CodedInputStream input = CodedInputStream.newInstance(bytes);
        input.setSizeLimit(65536);
        input.setRecursionLimit(16);
        inspect(input, Cloud.Envelope.getDescriptor(), 0, new int[] {0}, "", new HashMap<>());
        Cloud.Envelope result = Cloud.Envelope.parseFrom(bytes);
        if (result.getMajor() != 1 || result.getBodyCase() == Cloud.Envelope.BodyCase.BODY_NOT_SET) {
            throw new IOException("Unsupported or absent envelope body");
        }
        return result;
    }

    private static int charge(Map<String, Integer> counts, String path, Descriptors.FieldDescriptor field) throws IOException {
        int count = counts.merge(path, 1, Integer::sum);
        String parent = field.getContainingType().getName();
        int limit = parent.equals("Event") ? 16 : parent.equals("YsmEvent") ? 64 : 512;
        if (count > limit) {
            throw new IOException("Repeated field exceeds budget");
        }
        return count;
    }

    private static int scalarWire(Descriptors.FieldDescriptor field) {
        return switch (field.getType()) {
            case DOUBLE, FIXED64, SFIXED64 -> 1;
            case FLOAT, FIXED32, SFIXED32 -> 5;
            default -> 0;
        };
    }

    private static void inspect(CodedInputStream input, Descriptors.Descriptor descriptor,
                                int depth, int[] fields, String path, Map<String, Integer> counts) throws IOException {
        if (depth > 16) {
            throw new IOException("Envelope nesting exceeds budget");
        }
        while (!input.isAtEnd()) {
            int tag = input.readTag();
            int number = tag >>> 3;
            int wire = tag & 7;
            if (number == 0 || wire == 3 || wire == 4 || ++fields[0] > 8192) {
                throw new IOException("Invalid protobuf field budget");
            }
            Descriptors.FieldDescriptor field = descriptor.findFieldByNumber(number);
            String child = path + "/" + number;
            if (field != null && field.isRepeated() && field.isPackable() && wire == 2) {
                int length = input.readRawVarint32();
                int previous = input.pushLimit(length);
                while (!input.isAtEnd()) {
                    charge(counts, child, field);
                    if (!input.skipField((number << 3) | scalarWire(field))) {
                        throw new IOException("Invalid packed field");
                    }
                }
                input.popLimit(previous);
                continue;
            }
            if (field != null && field.isRepeated()) {
                int count = charge(counts, child, field);
                child += "[" + count + "]";
            }
            if (field != null && field.getJavaType() == Descriptors.FieldDescriptor.JavaType.MESSAGE && wire == 2) {
                int length = input.readRawVarint32();
                if (length < 0 || length > 65536) {
                    throw new IOException("Invalid nested message length");
                }
                int previous = input.pushLimit(length);
                inspect(input, field.getMessageType(), depth + 1, fields, child, counts);
                input.popLimit(previous);
            } else if (field != null && field.getJavaType() == Descriptors.FieldDescriptor.JavaType.STRING && wire == 2) {
                int length = input.readRawVarint32();
                if (length < 0 || length > 4096) {
                    throw new IOException("String exceeds budget");
                }
                input.skipRawBytes(length);
            } else if (!input.skipField(tag)) {
                throw new IOException("Invalid protobuf group");
            }
        }
    }
}
