/**
 * 
 */
package com.browseengine.bobo.geosearch.query;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.Version;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.browseengine.bobo.geosearch.IGeoConverter;
import com.browseengine.bobo.geosearch.bo.CartesianGeoRecord;
import com.browseengine.bobo.geosearch.bo.LatitudeLongitudeDocId;
import com.browseengine.bobo.geosearch.impl.CartesianGeoRecordComparator;
import com.browseengine.bobo.geosearch.impl.CartesianGeoRecordSerializer;
import com.browseengine.bobo.geosearch.impl.GeoConverter;
import com.browseengine.bobo.geosearch.impl.GeoRecordBTree;
import com.browseengine.bobo.geosearch.index.impl.GeoAtomicReader;
import com.browseengine.bobo.geosearch.index.impl.GeoSegmentReader;
import com.browseengine.bobo.geosearch.util.StubAtomicReader;

/**
 * @author Ken McCracken
 *
 * TODO:  Much of this test assumes that the scorer will be receiving a reader over multiple segments.  
 * In lucene 4 the scorer receives an AtomicReader so this appears to be a bad assumption.  This test could use some clean up 
 */
public class GeoScorerTest {

    private IGeoConverter geoConverter;
    double centroidLongitude; double centroidLatitude; Float rangeInKm;
    private GeoQuery geoQuery;
    private IndexSearcher searcher;
    private Weight geoWeight;
    private Scorer scorer1;
    private Scorer scorer2;
    private Scorer scorer3;
    private List<GeoSegmentReader<CartesianGeoRecord>> geoSubReaders;
    private GeoSegmentReader<CartesianGeoRecord> geoSegmentReader;
    
    private List<LatitudeLongitudeDocId> indexedDocuments;
    
    private LatitudeLongitudeDocId MISS_FAR_WEST;
    private LatitudeLongitudeDocId HIT0_CLOSE;
    private LatitudeLongitudeDocId MISS_FAR_SOUTH;
    private LatitudeLongitudeDocId HIT_EXACT;
    private LatitudeLongitudeDocId HIT1_CLOSE;
    private LatitudeLongitudeDocId HIT2_CLOSE;
    private LatitudeLongitudeDocId MISS_NOT_BY_MUCH;
    
    private int docid;
    private float score;
    
    private int maxDoc2;
    private TreeSet<CartesianGeoRecord> treeSet2;
    private GeoRecordBTree geoRecordBTree2;
    private GeoSegmentReader<CartesianGeoRecord> geoSegmentReader2;
    
    private static class MyGeoSegmentReader extends GeoSegmentReader<CartesianGeoRecord> {
        private final GeoRecordBTree tree;
        
        public MyGeoSegmentReader(GeoRecordBTree tree, int maxDoc) {
            super(tree.getArrayLength(), maxDoc, new CartesianGeoRecordSerializer(), 
                    new CartesianGeoRecordComparator());
            this.tree = tree;
        }
        
        /**
         * Delegates to the GeoRecordBTree from the constructor.
         * 
         * {@inheritDoc}
         */
        @Override
        protected CartesianGeoRecord getValueAtIndex(int index) {
            return tree.getValueAtIndex(index);
        }
    }
    
    TreeSet<CartesianGeoRecord> treeSet;
    
    private List<LatitudeLongitudeDocId> indexedDocuments3;
    
    @Before
    public void setUp() throws Exception {
        geoConverter = new GeoConverter();
        
        centroidLongitude = -71.61f;
        centroidLatitude = 42.42f;
        rangeInKm = 5f*1.609344f;

        docid = 0;
        indexedDocuments = new ArrayList<LatitudeLongitudeDocId>();
        MISS_FAR_WEST = new LatitudeLongitudeDocId(centroidLatitude, centroidLongitude-20, docid++);
        indexedDocuments.add(MISS_FAR_WEST);
        HIT0_CLOSE = new LatitudeLongitudeDocId(centroidLatitude - 0.00001f, centroidLongitude + 0.0001f, docid++);
        indexedDocuments.add(HIT0_CLOSE);
        MISS_FAR_SOUTH = new LatitudeLongitudeDocId(centroidLatitude - 35, centroidLongitude, docid++);
        indexedDocuments.add(MISS_FAR_SOUTH);
        HIT_EXACT = new LatitudeLongitudeDocId(centroidLatitude, centroidLongitude, docid++);
        indexedDocuments.add(HIT_EXACT);
        HIT1_CLOSE = new LatitudeLongitudeDocId(centroidLatitude + 0.00033f, centroidLongitude - 0.00023f, docid++);
        indexedDocuments.add(HIT1_CLOSE);
        HIT2_CLOSE = new LatitudeLongitudeDocId(centroidLatitude + 0.00011f, centroidLongitude - 0.00034f, docid++);
        indexedDocuments.add(HIT2_CLOSE);
        
        indexedDocuments3 = new ArrayList<LatitudeLongitudeDocId>();
        
        geoSubReaders = new ArrayList<GeoSegmentReader<CartesianGeoRecord>>();
    }
    
    private LatitudeLongitudeDocId HIT3_CLOSE;
    private LatitudeLongitudeDocId MISS_EAST;
    private LatitudeLongitudeDocId SF_CENTER;
    private LatitudeLongitudeDocId HIT4_CLOSE;
    private LatitudeLongitudeDocId SF_OTHER1;
    private LatitudeLongitudeDocId SF_OTHER2;
    private Set<CartesianGeoRecord> treeSet3;
    private GeoRecordBTree geoRecordBTree3;
    private GeoSegmentReader<CartesianGeoRecord> geoSegmentReader3;
    private int maxDoc3;
    
    private void setupThirdSegment() throws Exception {
        // docids are within the segment because segments can get re-ordered and stacked 
        // arbitrarily from the perspective outside the segments.gen and segments file.
        docid = 0;
        HIT3_CLOSE = new LatitudeLongitudeDocId(centroidLatitude + 0.00007f, centroidLongitude + 0.00009f, docid++);
        indexedDocuments3.add(HIT3_CLOSE);
        MISS_EAST = new LatitudeLongitudeDocId(centroidLatitude + 10.00004f, centroidLongitude + 10.1001f, docid++);
        indexedDocuments3.add(MISS_EAST);
        SF_CENTER = new LatitudeLongitudeDocId(37.91f, -122.50f, docid++);
        indexedDocuments3.add(SF_CENTER);
        HIT4_CLOSE = new LatitudeLongitudeDocId(centroidLatitude + 0.00002f, centroidLongitude + 0.00016f, docid++);
        indexedDocuments3.add(HIT4_CLOSE);
        SF_OTHER1 = new LatitudeLongitudeDocId(SF_CENTER.latitude + 0.00007f, SF_CENTER.longitude + 0.00003f, docid++);
        indexedDocuments3.add(SF_OTHER1);
        SF_OTHER2 = new LatitudeLongitudeDocId(SF_CENTER.latitude + 0.00007f, SF_CENTER.longitude - 0.00003f, docid++);
        indexedDocuments3.add(SF_OTHER2);
        
        treeSet3 = new TreeSet<CartesianGeoRecord>(new CartesianGeoRecordComparator());
        for (LatitudeLongitudeDocId raw : indexedDocuments3) {
            CartesianGeoRecord geoRecord = geoConverter.toCartesianGeoRecord(raw, CartesianGeoRecord.DEFAULT_FILTER_BYTE);
            treeSet3.add(geoRecord);
        }
        geoRecordBTree3 = new GeoRecordBTree(treeSet3);
        maxDoc3 = treeSet3.size();
        geoSegmentReader3 = new MyGeoSegmentReader(geoRecordBTree3, maxDoc3);
        geoSubReaders.add(geoSegmentReader3);
    }
    
    private void addMissNotByMuch() {
        MISS_NOT_BY_MUCH = new LatitudeLongitudeDocId(centroidLatitude + 0.09f, centroidLongitude - 0.08f, docid++);
        indexedDocuments.add(MISS_NOT_BY_MUCH);
    }
    
    private void setupOneTreeAndReaders() throws Exception {
        setupOneTree();
        setupQuerySearcherReaderScorer();
    }
    
    int maxDoc;
    private GeoAtomicReader geoIndexReader1;
    private GeoAtomicReader geoIndexReader2;
    private GeoAtomicReader geoIndexReader3;
    
    private void setupOneTree() throws Exception {
        
        treeSet = new TreeSet<CartesianGeoRecord>(new CartesianGeoRecordComparator());
        for (LatitudeLongitudeDocId raw : indexedDocuments) {
            CartesianGeoRecord geoRecord = geoConverter.toCartesianGeoRecord(raw, CartesianGeoRecord.DEFAULT_FILTER_BYTE);
            treeSet.add(geoRecord);
        }
        
        GeoRecordBTree geoRecordBTree = new GeoRecordBTree(treeSet);
        maxDoc = indexedDocuments.size();
        geoSegmentReader = new MyGeoSegmentReader(geoRecordBTree, maxDoc);
        
        geoSubReaders.add(geoSegmentReader);
    }
    
    private void setupQuerySearcherReaderScorer() throws Exception {
        searcher = null;
        
        Directory directory = buildEmptyDirectory();
        
        
        geoQuery = new GeoQuery(centroidLatitude, centroidLongitude, rangeInKm);
        geoWeight = geoQuery.createWeight(searcher);
        boolean scoreDocsInOrder = true;
        boolean topScorer = true;

       
        geoIndexReader1 = new GeoAtomicReader(new StubAtomicReader("_0"), geoSegmentReader);
        scorer1 = geoWeight.scorer(geoIndexReader1.getContext(), scoreDocsInOrder, topScorer, 
                new Bits.MatchAllBits(maxDoc));
        geoIndexReader2 = new GeoAtomicReader(new StubAtomicReader("_1"), geoSegmentReader2);
        scorer2 = geoWeight.scorer(geoIndexReader2.getContext(), scoreDocsInOrder, topScorer, 
                new Bits.MatchAllBits(maxDoc2));
        geoIndexReader3 = new GeoAtomicReader(new StubAtomicReader("_3"), geoSegmentReader3);
        scorer3 = geoWeight.scorer(geoIndexReader3.getContext(), scoreDocsInOrder, topScorer, 
                new Bits.MatchAllBits(maxDoc3));
      
    }
    
    private Directory buildEmptyDirectory() throws IOException {
        RAMDirectory directory = new RAMDirectory();
        
        Version version = Version.LUCENE_CURRENT;
        Analyzer analyzer =  new StandardAnalyzer(version);
        IndexWriterConfig indexWriterConfig = new IndexWriterConfig(version, analyzer);
        IndexWriter writer = new IndexWriter(directory, indexWriterConfig);
        
        writer.close();
        
        return directory;
    }
    
    @After
    public void tearDown() throws Exception {
        geoIndexReader1.close();
        geoIndexReader2.close();
        geoIndexReader3.close();
    }
    
    
    private void setupOneTreeAndEmptySecondAndReaders() throws Exception {
        setupOneTree();
        addEmptySecondSegment();
        setupQuerySearcherReaderScorer();
        
    }
    
    private void setupOneTree_empty2nd_3rd_AndReaders() throws Exception {
        setupOneTree();
        addEmptySecondSegment();
        setupThirdSegment();
        
        setupQuerySearcherReaderScorer();
    }


    private void addEmptySecondSegment() throws Exception {
        treeSet2 = new TreeSet<CartesianGeoRecord>(new CartesianGeoRecordComparator());
        maxDoc2 = 6;
        geoRecordBTree2 = new GeoRecordBTree(treeSet2);
        geoSegmentReader2 = new MyGeoSegmentReader(geoRecordBTree2, maxDoc2);
        geoSubReaders.add(geoSegmentReader2);
    }
    
    @Test
    public void test_advance_and_score6() throws Exception {
        setupOneTreeAndReaders();
        
        verifyAdvanceAndScore();
    }
    
    @Test
    public void test_advance_and_score6_empty2nd() throws Exception {
        setupOneTreeAndEmptySecondAndReaders();
        
        verifyAdvanceAndScore();
    }
    
    @Test
    public void test_advance_and_score6_empty2nd_3rd() throws Exception {
        setupOneTree_empty2nd_3rd_AndReaders();
        
        verifyAdvanceAndScore_3segments();
    }
    
    @Test
    public void test_advance_and_score6_empty2nd_3rd_jump() throws Exception {
        setupOneTree_empty2nd_3rd_AndReaders();
        
        verifyAdvanceAndScore_3segments_jumpTo3rd();
    }
    
    @Test
    public void test_advance_and_score7() throws Exception {
        addMissNotByMuch();
        setupOneTreeAndReaders();
        
        verifyAdvanceAndScore();
    }
    
    @Test
    public void test_advance_and_score7_empty2nd() throws Exception {
        addMissNotByMuch();
        setupOneTreeAndEmptySecondAndReaders();
        
        verifyAdvanceAndScore();
    }
    
    @Test
    public void test_advance_and_score7_empty2nd_3rd() throws Exception {
        addMissNotByMuch();
        setupOneTree_empty2nd_3rd_AndReaders();
        
        verifyAdvanceAndScore_3segments();
    }
    
    @Test
    public void test_advance_and_score7_empty2nd_3rd_jump() throws Exception {
        addMissNotByMuch();
        setupOneTree_empty2nd_3rd_AndReaders();
        
        verifyAdvanceAndScore_3segments_jumpTo3rd();
    }
    
    @Test
    public void test_nextDoc_and_score6() throws Exception {
        setupOneTreeAndReaders();
        
        verifyNextDocAndScore();
    }

    @Test
    public void test_nextDoc_and_score6_empty2nd() throws Exception {
        setupOneTreeAndEmptySecondAndReaders();
        
        verifyNextDocAndScore();
    }
    
    @Test
    public void test_nextDoc_and_score6_empty2nd_3rd() throws Exception {
        setupOneTree_empty2nd_3rd_AndReaders();
        
        verifyNextDocAndScore3segments();
    }
    
    @Test
    public void test_nextDoc_and_score7() throws Exception {
        addMissNotByMuch();
        setupOneTreeAndReaders();
        
        verifyNextDocAndScore();
    }
    
    @Test
    public void test_nextDoc_and_score7_empty2nd() throws Exception {
        addMissNotByMuch();
        setupOneTreeAndEmptySecondAndReaders();
        
        verifyNextDocAndScore();
    }
    
    @Test
    public void test_nextDoc_and_score7_empty2nd_3rd() throws Exception {
        addMissNotByMuch();
        setupOneTree_empty2nd_3rd_AndReaders();
        
        verifyNextDocAndScore3segments();
    }
    
    @Test
    public void test_noSegments() throws Exception {
        setupQuerySearcherReaderScorer();
        verifyEndNextDoc(scorer1);
    }
    
    @Test
    public void test_sf_nextDoc_and_score7_empty2nd_3rd() throws Exception {
        addMissNotByMuch();
        setupOneTree_empty2nd_3rd_AndReaders();
        centroidLongitude = SF_CENTER.longitude + 0.0001f;
        centroidLatitude = SF_CENTER.latitude - 0.00002f;
        setupQuerySearcherReaderScorer();
        
        verifyNextDocAndScoreSF();
    }
    
    private void verifyAdvanceAndScore() throws Exception {
        verifyAdvanceAndScore_1segment();
        verifyEndAdvance(maxDoc);
    }
    
    private void verifyAdvanceAndScore_3segments_jumpTo3rd() throws Exception {
        verifyAdvanceAndScore_segment3();
        verifyEndAdvance(maxDoc + maxDoc2 + maxDoc3);
    }
    
    private void verifyAdvanceAndScore_3segments() throws Exception {
        verifyAdvanceAndScore_1segment();
        verifyAdvanceAndScore_segment3();
        verifyEndAdvance(maxDoc + maxDoc2 + maxDoc3);
    }
    
    private void verifyAdvanceAndScore_segment3() throws Exception {
        float expectedScoreLowerBound = 0.00001f;
        float expectedScoreUpperBound = 1f;

        int advanceTo = SF_CENTER.docid;
        
        int expectedDocid =  HIT4_CLOSE.docid;
        docid = scorer3.advance(advanceTo);
        assertTrue("expectedDocid "+expectedDocid+", got actual docid "+docid, expectedDocid == docid);
        
        score = scorer3.score();
        assertTrue("docid "+docid+", expectedScoreLowerBound "+expectedScoreLowerBound+", expectedScoreUpperBound "+expectedScoreUpperBound+", actual score "+score, 
                expectedScoreLowerBound <= score && score <= expectedScoreUpperBound);

    }
    
    private void verifyAdvanceAndScore_1segment() throws Exception {
        // advance to second hit docid=3
        
        docid = scorer1.advance(3);
        
        int expectedDocid = HIT_EXACT.docid;
        assertTrue("expectedDocid "+expectedDocid+", got docid "+docid, expectedDocid == docid);
        
         score = scorer1.score();
        float expectedScoreLowerBound = 0.00001f;
        float expectedScoreUpperBound = 1f;
        assertTrue("docid "+docid+", expectedScoreLowerBound "+expectedScoreLowerBound+", expectedScoreUpperBound "+expectedScoreUpperBound+", actual score "+score, 
                expectedScoreLowerBound <= score && score <= expectedScoreUpperBound);
        
        // third hit should be docid=4
         docid = scorer1.advance(4);
        
        expectedDocid = HIT1_CLOSE.docid;
        assertTrue("expectedDocid "+expectedDocid+", got docid "+docid, expectedDocid == docid);
        
         score = scorer1.score();
         expectedScoreLowerBound = 0.00001f;
        assertTrue("docid "+docid+", expectedScoreLowerBound "+expectedScoreLowerBound+", expectedScoreUpperBound "+expectedScoreUpperBound+", actual score "+score, 
                expectedScoreLowerBound <= score && score <= expectedScoreUpperBound);
    }
    
    private void verifyEndAdvance(int advanceTo) throws IOException {
        int expectedDocid;
        for (int i = 0; i < 10; i++) {
            // no more hits
            docid = advanceTo(advanceTo);
        
            expectedDocid = DocIdSetIterator.NO_MORE_DOCS;
            assertTrue("expectedDocid "+expectedDocid+", got docid "+docid, expectedDocid == docid);
        }

    }
    
    private int advanceTo(int advanceTo) throws IOException {
        if (advanceTo < maxDoc) {
            return scorer1.advance(advanceTo);
        } 

        advanceTo -= maxDoc;
        
        if (advanceTo < maxDoc2) {
            return scorer2.advance(advanceTo);
        }
        
        advanceTo -= maxDoc2;
        return scorer3.advance(advanceTo);
    }
    
    private void verifyNextDocAndScore() throws Exception {
        verifyNextDocAndScore_1segment();
        verifyEndNextDoc(scorer1);

    }
    
    private void verifyNextDocAndScore3segments() throws Exception {
        verifyNextDocAndScore_1segment();
        
        int expectedDocid;
        float expectedScoreLowerBound = 0.00001f;
        float expectedScoreUpperBound = 1f;
        
        // next hit should be on segment 3
        verifyEndNextDoc(scorer1);
        verifyEndNextDoc(scorer2);
         docid = scorer3.nextDoc();
        
         expectedDocid = HIT3_CLOSE.docid;
        assertTrue("expectedDocid "+expectedDocid+", got docid "+docid, expectedDocid == docid);
        
         score = scorer3.score();
        assertTrue("docid "+docid+", expectedScoreLowerBound "+expectedScoreLowerBound
                +", expectedScoreUpperBound "+expectedScoreUpperBound+", actual score "+score, 
                expectedScoreLowerBound <= score && score <= expectedScoreUpperBound);
        
        // next hit is HIT4.
        docid = scorer3.nextDoc();
       
       expectedDocid = HIT4_CLOSE.docid;
       assertTrue("expectedDocid "+expectedDocid+", got docid "+docid, expectedDocid == docid);
       
        score = scorer3.score();
       assertTrue("docid "+docid+", expectedScoreLowerBound "+expectedScoreLowerBound
               +", expectedScoreUpperBound "+expectedScoreUpperBound+", actual score "+score, 
               expectedScoreLowerBound <= score && score <= expectedScoreUpperBound);

       // no more hits
       verifyEndNextDoc(scorer3);
    }
    
    private void verifyNextDocAndScore_1segment() throws Exception {
        // first hit should be docid=1
         docid = scorer1.nextDoc();
        
        int expectedDocid = HIT0_CLOSE.docid;
        assertTrue("expectedDocid "+expectedDocid+", got docid "+docid, expectedDocid == docid);
        
         score = scorer1.score();
        float expectedScoreLowerBound = 0.00001f;
        float expectedScoreUpperBound = 1f;
        assertTrue("docid "+docid+", expectedScoreLowerBound "+expectedScoreLowerBound+", expectedScoreUpperBound "+expectedScoreUpperBound+", actual score "+score, 
                expectedScoreLowerBound <= score && score <= expectedScoreUpperBound);
        
        // second hit should be docid=3
         docid = scorer1.nextDoc();
        
        expectedDocid = HIT_EXACT.docid;
        assertTrue("expectedDocid "+expectedDocid+", got docid "+docid, expectedDocid == docid);
        
         score = scorer1.score();
         expectedScoreLowerBound = 0.99f;
        assertTrue("docid "+docid+", expectedScoreLowerBound "+expectedScoreLowerBound+", expectedScoreUpperBound "+expectedScoreUpperBound+", actual score "+score, 
                expectedScoreLowerBound <= score && score <= expectedScoreUpperBound);

        // third hit should be docid=4
         docid = scorer1.nextDoc();
        
        expectedDocid = HIT1_CLOSE.docid;
        assertTrue("expectedDocid "+expectedDocid+", got docid "+docid, expectedDocid == docid);
        
         score = scorer1.score();
         expectedScoreLowerBound = 0.00001f;
        assertTrue("docid "+docid+", expectedScoreLowerBound "+expectedScoreLowerBound+", expectedScoreUpperBound "+expectedScoreUpperBound+", actual score "+score, 
                expectedScoreLowerBound <= score && score <= expectedScoreUpperBound);

        // fourth hit should be docid=5
         docid = scorer1.nextDoc();
        
        expectedDocid = HIT2_CLOSE.docid;
        assertTrue("expectedDocid "+expectedDocid+", got docid "+docid, expectedDocid == docid);
        
         score = scorer1.score();
         expectedScoreLowerBound = 0.00001f;
        assertTrue("docid "+docid+", expectedScoreLowerBound "+expectedScoreLowerBound+", expectedScoreUpperBound "+expectedScoreUpperBound+", actual score "+score, 
                expectedScoreLowerBound <= score && score <= expectedScoreUpperBound);

    }
    
    private void verifyNextDocAndScoreSF() throws Exception {
        int expectedDocid;
        float expectedScoreLowerBound = 0.00001f;
        float expectedScoreUpperBound = 1f;

        // next hit should be SF_CENTER
        docid = scorer3.nextDoc();
        expectedDocid = SF_CENTER.docid;
        assertTrue("expectedDocid "+expectedDocid+", got docid "+docid, expectedDocid == docid);
        
         score = scorer3.score();
        assertTrue("docid "+docid+", expectedScoreLowerBound "+expectedScoreLowerBound
                +", expectedScoreUpperBound "+expectedScoreUpperBound+", actual score "+score, 
                expectedScoreLowerBound <= score && score <= expectedScoreUpperBound);
        
        // next hit should be SF_OTHER1
        docid = scorer3.nextDoc();
        expectedDocid = SF_OTHER1.docid;
        assertTrue("expectedDocid "+expectedDocid+", got docid "+docid, expectedDocid == docid);
        
         score = scorer3.score();
        assertTrue("docid "+docid+", expectedScoreLowerBound "+expectedScoreLowerBound
                +", expectedScoreUpperBound "+expectedScoreUpperBound+", actual score "+score, 
                expectedScoreLowerBound <= score && score <= expectedScoreUpperBound);

        // next hit should be SF_OTHER2
        docid = scorer3.nextDoc();
        expectedDocid = SF_OTHER2.docid;
        assertTrue("expectedDocid "+expectedDocid+", got docid "+docid, expectedDocid == docid);
        
         score = scorer3.score();
        assertTrue("docid "+docid+", expectedScoreLowerBound "+expectedScoreLowerBound
                +", expectedScoreUpperBound "+expectedScoreUpperBound+", actual score "+score, 
                expectedScoreLowerBound <= score && score <= expectedScoreUpperBound);


        verifyEndNextDoc(scorer3);
    }

    
    private void verifyEndNextDoc(Scorer scorer) throws Exception {
        int expectedDocid;
        
        for (int i = 0; i < 10; i++) {
            // no more hits
            docid = scorer.nextDoc();
        
            expectedDocid = DocIdSetIterator.NO_MORE_DOCS;
            assertTrue("expectedDocid "+expectedDocid+", got docid "+docid, expectedDocid == docid);
        }

    }

}
