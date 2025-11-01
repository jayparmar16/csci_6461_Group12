/**
 * Utility class for number conversions.
 */
class Utils {
    public short octalToShort(String octal) {
        return (short) Integer.parseInt(octal, 8);
    }

    public String shortToOctal(short value, int digits) {
        return String.format("%0" + digits + "o", value);
    }

    public String shortToBinary(short value, int bits) {
        return String.format("%" + bits + "s", Integer.toBinaryString(value & 0xFFFF)).replace(' ', '0');
    }
}