/*
 * Copyright 2009-2011 Roger Kapsi
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.ardverk.coding;

import java.io.DataInput;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * An implementation of {@link InputStream} that can decode 
 * Bencoded (Bee-Encoded) data.
 */
public class BencodingInputStream extends PushbackInputStream implements DataInput {

    /**
     * The charset that is being used for {@link String}s.
     */
    private final String charset;
    
    /**
     * Whether or not all byte-Arrays should be decoded as {@link String}s.
     */
    private final boolean decodeAsString;
    
    /**
     * Creates a {@link BencodingInputStream} with the default encoding.
     */
    public BencodingInputStream(InputStream in) {
        this(in, BencodingUtils.UTF_8, false);
    }
    
    /**
     * Creates a {@link BencodingInputStream} with the given encoding.
     */
    public BencodingInputStream(InputStream in, String charset) {
        this(in, charset, false);
    }
    
    /**
     * Creates a {@link BencodingInputStream} with the default encoding.
     */
    public BencodingInputStream(InputStream in, boolean decodeAsString) {
        this(in, BencodingUtils.UTF_8, decodeAsString);
    }
    
    /**
     * Creates a {@link BencodingInputStream} with the given encoding.
     */
    public BencodingInputStream(InputStream in, 
            String charset, boolean decodeAsString) {
        super(in);
        
        if (charset == null) {
            throw new NullPointerException("charset");
        }
        
        this.charset = charset;
        this.decodeAsString = decodeAsString;
    }
    
    /**
     * Returns the charset that is used to decode {@link String}s.
     * The default value is UTF-8.
     */
    public String getCharset() {
        return charset;
    }
    
    /**
     * Returns true if all byte-Arrays are being turned into {@link String}s.
     */
    public boolean isDecodeAsString() {
        return decodeAsString;
    }
    
    protected int pop() throws IOException {
        int value = read();
        if (value == -1) {
            throw new EOFException();
        }
        return value;
    }
    
    protected int peek() throws IOException {
        int value = pop();
        unread(value);
        return value;
    }
    
    private byte[] raw() throws IOException {
        return raw(pop());
    }
    
    private byte[] raw(int length) throws IOException {
        byte[] data = new byte[length];
        readFully(data);
        return data;
    }
    
    public byte[] readBytes() throws IOException {
        StringBuilder sb = new StringBuilder();
        
        int value = -1;
        while ((value = pop()) != BencodingUtils.LENGTH_DELIMITER) {
            sb.append((char)value);
        }
        
        return raw(Integer.parseInt(sb.toString()));
    }
    
    /**
     * Reads and returns an {@link Object}.
     */
    public Object readObject() throws IOException {
        int token = peek();
        
        if (token == BencodingUtils.DICTIONARY) {
            return readMap();            
        } else if (token == BencodingUtils.LIST) {
            return readList();
        } else if (token == BencodingUtils.NUMBER) {
            return readNumber();
        } else if (isDigit(token)) {
            byte[] data = raw();
            return decodeAsString ? new String(data, charset) : data;
        } else {
            return readCustom();
        }
    }
    
    protected Object readCustom() throws IOException {
        throw new IOException();
    }
    
    /**
     * Reads and returns a {@link String}.
     */
    public String readString() throws IOException {
        return readString(charset);
    }
    
    /**
     * Reads and returns a {@link String}.
     */
    public String readString(String encoding) throws IOException {
        return new String(readBytes(), encoding);
    }
    
    /**
     * Reads and returns an {@link Enum}.
     */
    public <T extends Enum<T>> T readEnum(Class<T> clazz) throws IOException {
        return Enum.valueOf(clazz, readString());
    }
    
    /**
     * Reads and returns a {@link Number}.
     */
    public Number readNumber() throws IOException {
        if (pop() != BencodingUtils.NUMBER) {
            throw new IOException();
        }
        
        StringBuilder sb = new StringBuilder();
        
        boolean decimal = false;
        int token = -1;
        
        while ((token = pop()) != BencodingUtils.EOF) {
            if (token == '.') {
                decimal = true;
            }
            
            sb.append((char)token);
        }
        
        try {
            if (decimal) {
                return new BigDecimal(sb.toString());
            } else {
                return new BigInteger(sb.toString());
            }
        } catch (NumberFormatException err) {
            throw new IOException("NumberFormatException", err);
        }
    }
    
    /**
     * Reads and returns an array of {@link Object}s
     */
    public Object[] readArray() throws IOException {
        return readList().toArray();
    }
    
    /**
     * Reads and returns an array of {@link Object}s
     */
    public <T> T[] readArray(T[] a) throws IOException {
        return readList().toArray(a);
    }
    
    /**
     * Reads and returns a {@link List}.
     */
    public List<Object> readList() throws IOException {
        return readList(Object.class);
    }
    
    /**
     * Reads and returns a {@link List}.
     */
    public <T> List<T> readList(Class<T> clazz) throws IOException {
        return readCollection(new ArrayList<T>(), clazz);
    }
    
    /**
     * Reads and returns a {@link Collection}.
     */
    public <T extends Collection<Object>> T readCollection(T dst) throws IOException {
        return readCollection(dst, Object.class);
    }
    
    /**
     * Reads and returns a {@link Collection}.
     */
    public <E, T extends Collection<E>> T readCollection(T dst, Class<E> clazz) throws IOException {
        if (pop() != BencodingUtils.LIST) {
            throw new IOException();
        }
        
        while (peek() != BencodingUtils.EOF) {
            dst.add(clazz.cast(readObject()));
        }
        
        return dst;
    }

    /**
     * Reads and returns a {@link Map}.
     */
    public Map<String, Object> readMap() throws IOException {
        return readMap(Object.class);
    }
    
    /**
     * Reads and returns a {@link Map}.
     */
    public Map<String, Object> readMap(Map<String, Object> dst) throws IOException {
        return readMap(dst, Object.class);
    }
    
    /**
     * Reads and returns a {@link Map}.
     */
    public <T> Map<String, T> readMap(Class<T> clazz) throws IOException {
        return readMap(new TreeMap<String, T>(), clazz);
    }
    
    /**
     * Reads and returns a {@link Map}.
     */
    public <T> Map<String, T> readMap(Map<String, T> dst, Class<T> clazz) throws IOException {
        if (pop() != BencodingUtils.DICTIONARY) {
            throw new IOException();
        }
        
        while (peek() != BencodingUtils.EOF) {
            String key = new String(raw(), charset);
            T value = clazz.cast(readObject());
            dst.put(key, value);
        }
        
        return dst;
    }
    
    /**
     * Reads and returns a char.
     */
    @Override
    public char readChar() throws IOException {
        return readString().charAt(0);
    }
    
    /**
     * Reads and returns a boolean.
     */
    @Override
    public boolean readBoolean() throws IOException {
        return readInt() != 0;
    }
    
    /**
     * Reads and returns a byte.
     */
    @Override
    public byte readByte() throws IOException {
        return readNumber().byteValue();
    }
    
    /**
     * Reads and returns a short.
     */
    @Override
    public short readShort() throws IOException {
        return readNumber().shortValue();
    }
    
    /**
     * Reads and returns an int.
     */
    @Override
    public int readInt() throws IOException {
        return readNumber().intValue();
    }
    
    /**
     * Reads and returns a float.
     */
    @Override
    public float readFloat() throws IOException {
        return readNumber().floatValue();
    }
    
    /**
     * Reads and returns a long.
     */
    @Override
    public long readLong() throws IOException {
        return readNumber().longValue();
    }
    
    /**
     * Reads and returns a double.
     */
    @Override
    public double readDouble() throws IOException {
        return readNumber().doubleValue();
    }
    
    /**
     * @see DataInput#readFully(byte[])
     */
    @Override
    public void readFully(byte[] dst) throws IOException {
        readFully(dst, 0, dst.length);
    }
    
    /**
     * @see DataInput#readFully(byte[], int, int)
     */
    @Override
    public void readFully(byte[] dst, int off, int len) throws IOException {
        int total = 0;
        
        while (total < len) {
            int r = read(dst, total, len-total);
            if (r == -1) {
                throw new EOFException();
            }
            
            total += r;
        }
    }

    /**
     * Reads and returns a {@link String}.
     * 
     * @see #readString().
     */
    @Override
    public String readLine() throws IOException {
        return readString();
    }

    /**
     * Reads and returns an unsigned byte.
     */
    @Override
    public int readUnsignedByte() throws IOException {
        return readByte() & 0xFF;
    }

    /**
     * Reads and returns an unsigned short.
     */
    @Override
    public int readUnsignedShort() throws IOException {
        return readShort() & 0xFFFF;
    }

    /**
     * Reads and returns an UTF encoded {@link String}.
     */
    @Override
    public String readUTF() throws IOException {
        return readString(BencodingUtils.UTF_8);
    }

    /**
     * Skips the given number of bytes.
     */
    @Override
    public int skipBytes(int n) throws IOException {
        return (int)skip(n);
    }
    
    /**
     * Returns true if the given token is a digit.
     */
    private static boolean isDigit(int token) {
        return '0' <= token && token <= '9';
    }
}
