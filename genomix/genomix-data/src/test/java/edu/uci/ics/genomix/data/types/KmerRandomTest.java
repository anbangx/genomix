package edu.uci.ics.genomix.data.types;

import java.io.IOException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import edu.uci.ics.genomix.data.utils.GeneCode;

public class KmerRandomTest {

    public static int strMaxLength;
    public static int strMinLength;

    @Before
    public void setUp() {
        strMaxLength = 9;
        strMinLength = 6;
        if ((strMinLength <= 0) || (strMaxLength <= 0)) {
            throw new IllegalStateException("strMinLength or strMaxLength can not be less than 0!");
        }
        if (strMinLength > strMaxLength) {
            throw new IllegalStateException("strMinLength can not be larger than strMaxLength!");
        }
    }

    public static Kmer getRandomKmer(int strMinLength, int strMaxLength, String input, int strLength) {
        int kmerSize = RandomTestHelper.genRandomInt(1, strLength);
        String actualKmerStr = input.substring(0, kmerSize);
        Kmer.setGlobalKmerLength(kmerSize);
        Kmer kmer = new Kmer();
        kmer.setFromStringBytes(actualKmerStr.getBytes(), 0);
        return kmer;
    }

    @Test
    public void TestCompressKmer() throws IOException {
        int strLength = RandomTestHelper.genRandomInt(strMinLength, strMaxLength);
        String input = RandomTestHelper.generateGeneString(strLength);
        int kmerSize = RandomTestHelper.genRandomInt(1, strLength);
        String actualKmerStr = input.substring(0, kmerSize);
        Kmer.setGlobalKmerLength(kmerSize);
        Kmer kmer = new Kmer();
        kmer.setFromStringBytes(actualKmerStr.getBytes(), 0);
        Assert.assertEquals(actualKmerStr, kmer.toString());
    }

    @Test
    public void TestMoveKmer() {
        int strLength = RandomTestHelper.genRandomInt(strMinLength, strMaxLength);
        String input = RandomTestHelper.generateGeneString(strLength);
        Kmer kmer = getRandomKmer(strMinLength, strMaxLength, input, strLength);
        int kmerLength = kmer.getKmerLength();
        for (int i = kmerLength; i < strLength - 1; i++) {
            kmer.shiftKmerWithNextChar((byte) (input.charAt(i)));
            Assert.assertEquals(input.substring(1 + i - kmerLength, 1 + i - kmerLength + kmerLength), kmer.toString());
        }
    }

    @Test
    public void TestCompressKmerReverse() {
        int strLength = RandomTestHelper.genRandomInt(strMinLength, strMaxLength);
        String input = RandomTestHelper.generateGeneString(strLength);
        int kmerSize = RandomTestHelper.genRandomInt(1, strLength);
        String actualKmerStr = input.substring(0, kmerSize);
        Kmer.setGlobalKmerLength(kmerSize);
        Kmer kmer = new Kmer();
        kmer.setReversedFromStringBytes(actualKmerStr.getBytes(), 0);
        Assert.assertEquals(RandomTestHelper.getFlippedGeneStr(actualKmerStr), kmer.toString());
    }

    @Test
    public void TestGetGene() {
        int strLength = RandomTestHelper.genRandomInt(strMinLength, strMaxLength);
        String input = RandomTestHelper.generateGeneString(strLength);
        int kmerSize = RandomTestHelper.genRandomInt(1, strLength);
        String actualKmerStr = input.substring(0, kmerSize);
        Kmer.setGlobalKmerLength(kmerSize);
        Kmer kmer = new Kmer();
        kmer.setFromStringBytes(actualKmerStr.getBytes(), 0);
        for (int i = 0; i < kmer.getKmerLength(); i++) {
            Assert.assertEquals(actualKmerStr.charAt(i),
                    (char) (GeneCode.getSymbolFromCode(kmer.getGeneCodeAtPosition(i))));
        }
    }

    @Test
    public void TestGetOneByteFromKmer() {
        int strLength = RandomTestHelper.genRandomInt(strMinLength, strMaxLength);
        String input = RandomTestHelper.generateGeneString(strLength);
        int kmerSize = RandomTestHelper.genRandomInt(1, strLength);
        String actualKmerStr = input.substring(0, kmerSize);
        Kmer.setGlobalKmerLength(kmerSize);
        Kmer kmer = new Kmer();
        kmer.setFromStringBytes(actualKmerStr.getBytes(), 0);
        Kmer kmerAppend = new Kmer();
        kmer.setFromStringBytes(actualKmerStr.getBytes(), 0);
        for (int i = 0; i < kmer.getKmerLength(); i++) {
            byte byteActual = Kmer.getOneByteFromKmerAtPosition(i, kmer.getBytes(), kmer.getOffset(), kmer.getLength());
            byte byteExpect = GeneCode.getCodeFromSymbol((byte) (actualKmerStr.charAt(i)));
            for (int j = 1; j < 4 && i + j < kmer.getKmerLength(); j++) {
                byteExpect += GeneCode.getCodeFromSymbol((byte) (actualKmerStr.charAt(i + j))) << (i * 2);
            }
            Assert.assertEquals(byteActual, byteExpect);
            Kmer.appendOneByteAtPosition(i, byteActual, kmerAppend.getBytes(), kmerAppend.getOffset(),
                    kmerAppend.getLength());
        }
        Assert.assertEquals(kmer.toString(), kmerAppend.toString());
    }
}
