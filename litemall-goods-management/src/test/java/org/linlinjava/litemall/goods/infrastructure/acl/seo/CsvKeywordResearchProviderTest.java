package org.linlinjava.litemall.goods.infrastructure.acl.seo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.linlinjava.litemall.goods.application.seo.KeywordResearchProvider.KeywordDemand;
import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallSeoResearchProperties;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The file-backed source. These pin the two things that would corrupt the data silently:
 * quoted category names, and "unknown means unknown, not zero".
 */
public class CsvKeywordResearchProviderTest {

    private static final String HEADER =
            "category_id,level,category,on_sale_goods,keyword,monthly_searches,"
                    + "competition,difficulty,variants_collapsed,flag";

    private CsvKeywordResearchProvider providerFor(Path csv) {
        LitemallSeoResearchProperties props = new LitemallSeoResearchProperties();
        props.setSource("file");
        props.setFile(csv == null ? null : csv.toString());
        return new CsvKeywordResearchProvider(props);
    }

    private Path write(Path dir, String... lines) throws Exception {
        Path p = dir.resolve("keywords.csv");
        Files.write(p, (HEADER + "\n" + String.join("\n", lines) + "\n").getBytes());
        return p;
    }

    @Test
    public void servesTermsForACategoryHighestDemandFirst(@TempDir Path dir) throws Exception {
        Path csv = write(dir,
                "1,L3,Wall Lamps,50,plug in wall lamps,14800,0.3,20,2,",
                "1,L3,Wall Lamps,50,outdoor wall lamps,27100,0.5,35,6,");

        List<KeywordDemand> demand = providerFor(csv).demandFor("Wall Lamps", 10);

        assertThat(demand).extracting(KeywordDemand::term)
                .containsExactly("outdoor wall lamps", "plug in wall lamps");
        assertThat(demand.get(0).monthlySearches()).isEqualTo(27100);
    }

    @Test
    public void handlesQuotedCategoryNamesContainingCommas(@TempDir Path dir) throws Exception {
        // "Kitchen, Dining & Bar" is a real category. A naive split on comma shifts every
        // later column on these rows, so the keyword silently becomes the goods count.
        Path csv = write(dir,
                "1,L2,\"Kitchen, Dining & Bar\",218,dinner sets,9900,0.4,30,3,");

        List<KeywordDemand> demand =
                providerFor(csv).demandFor("Kitchen, Dining & Bar", 10);

        assertThat(demand).hasSize(1);
        assertThat(demand.get(0).term()).isEqualTo("dinner sets");
        assertThat(demand.get(0).monthlySearches()).isEqualTo(9900);
    }

    @Test
    public void matchesCategoryCaseAndWhitespaceInsensitively(@TempDir Path dir) throws Exception {
        Path csv = write(dir, "1,L3,Garden Tools,148,garden tools,22200,0.5,30,5,");

        assertThat(providerFor(csv).demandFor("  garden TOOLS ", 10)).hasSize(1);
    }

    @Test
    public void skipsCompetitorBrandRows(@TempDir Path dir) throws Exception {
        Path csv = write(dir,
                "1,L3,Bedding Sets,63,bedding sets,40500,0.4,30,4,",
                "1,L3,Bedding Sets,63,tesco bedding sets,12100,0.4,30,1,brand");

        assertThat(providerFor(csv).demandFor("Bedding Sets", 10))
                .extracting(KeywordDemand::term)
                .containsExactly("bedding sets");
    }

    @Test
    public void treatsMissingVolumeAsUnknownNotZero(@TempDir Path dir) throws Exception {
        Path csv = write(dir,
                "1,L3,Pillows,76,cushion pads,,0.2,10,1,",
                "1,L3,Pillows,76,pillows,5400,0.2,10,1,");

        List<KeywordDemand> demand = providerFor(csv).demandFor("Pillows", 10);

        assertThat(demand).extracting(KeywordDemand::term)
                .containsExactly("pillows", "cushion pads");
        assertThat(demand.get(1).monthlySearches()).isNull();
    }

    @Test
    public void honoursTheLimit(@TempDir Path dir) throws Exception {
        Path csv = write(dir,
                "1,L3,Tool Sets,169,a,900,0.1,5,1,",
                "1,L3,Tool Sets,169,b,800,0.1,5,1,",
                "1,L3,Tool Sets,169,c,700,0.1,5,1,");

        assertThat(providerFor(csv).demandFor("Tool Sets", 2)).hasSize(2);
    }

    @Test
    public void unknownCategoryYieldsEmpty(@TempDir Path dir) throws Exception {
        Path csv = write(dir, "1,L3,Garden Tools,148,garden tools,22200,0.5,30,5,");

        assertThat(providerFor(csv).demandFor("Chandeliers", 10)).isEmpty();
    }

    @Test
    public void missingFileDegradesToEmptyRatherThanThrowing(@TempDir Path dir) {
        assertThat(providerFor(dir.resolve("nope.csv")).demandFor("Wall Lamps", 10)).isEmpty();
    }

    @Test
    public void unconfiguredFileDegradesToEmpty() {
        assertThat(providerFor(null).demandFor("Wall Lamps", 10)).isEmpty();
    }

    @Test
    public void theActualRawExportIsRejected(@TempDir Path dir) throws Exception {
        // VERBATIM header of category-keywords.csv. It carries category, keyword AND
        // monthly_searches, so a check for only those would accept it — and the raw file
        // keeps permutation groups, so "Wall Lamps" would serve six spellings of one term
        // as its top six. 'variants_collapsed' is the column that tells them apart.
        Path p = dir.resolve("raw.csv");
        Files.write(p, ("category_id,level,category,on_sale_goods,rank,keyword,"
                + "monthly_searches,competition,difficulty\n"
                + "1,L3,Wall Lamps,50,1,outdoor wall lamps,27100,0.5,35\n"
                + "1,L3,Wall Lamps,50,2,exterior wall lamps,27100,0.5,35\n").getBytes());

        LitemallSeoResearchProperties props = new LitemallSeoResearchProperties();
        props.setFile(p.toString());
        assertThat(new CsvKeywordResearchProvider(props).demandFor("Wall Lamps", 10)).isEmpty();
    }
}
