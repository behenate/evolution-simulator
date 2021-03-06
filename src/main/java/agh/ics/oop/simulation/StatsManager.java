package agh.ics.oop.simulation;

import agh.ics.oop.Utils;
import agh.ics.oop.dataTypes.Genome;
import agh.ics.oop.maps.AbstractWorldMap;
import agh.ics.oop.objects.Animal;
import agh.ics.oop.objects.IMapElement;
import com.opencsv.CSVWriter;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

// A class defining a single chart of some specific simulation statistics
class StatsChart{
    final VBox container;
    final NumberAxis xAxis = new NumberAxis();
    final NumberAxis yAxis = new NumberAxis();
    final XYChart.Series series = new XYChart.Series();

    final LineChart<Number,Number> lineChart = new LineChart<>(xAxis, yAxis);

    public StatsChart(String name, String colour){
        series.setName(name);
        lineChart.setCreateSymbols(false);
        lineChart.animatedProperty().set(false);
        lineChart.getData().add(series);
        Label label = new Label(name);
        label.setFont(new Font(15));
        lineChart.setMinHeight(10);
        container = new VBox(label, lineChart);
        container.setAlignment(Pos.CENTER);
        series.getNode().lookup(".chart-series-line").setStyle(String.format("-fx-stroke: %s;", colour));
    }
    public void addData(int x, float y){
        series.getData().add(new XYChart.Data(x, y));
    }
    public Node getUI(){
        return container;
    }
}

public class StatsManager {
    private final ArrayList<String[]> statsHist = new ArrayList<>();
    private final File outputFile;

    VBox mainContainer = new VBox();
    VBox chartsContainer = new VBox();
    private final AbstractWorldMap map;
    private final HashMap<Genome, Integer> genotypes = new HashMap<>();
    private Genome dominantGenome = new Genome(new int[32]);
    private int dominantGenotypeCount = 0;
    private final StatsChart animalChart = new StatsChart("Animals No." , "#ffbe0b");
    private final StatsChart plantChart = new StatsChart("Number Of Plants", "#fb5607");
    private final StatsChart energyChart = new StatsChart("Average Energy", "#ff006e");
    private final StatsChart lifespanChart = new StatsChart("Average Lifespan", "#8338ec");
    private final StatsChart childrenChart = new StatsChart("Number Of Children", "#3a86ff");
    private final StatsChart[] allCharts = {animalChart, plantChart, energyChart, lifespanChart, childrenChart};
    private final Text dominantGenotypeText = new Text("");

    private float animalsNumber = 0;
    private float plantNumber = 0;
    private float energySum = 0;
    private float lifetimeSum = 0;
    private float lifetimeSamples = 1;
    private float childrenCountSum = 0;

    private boolean genomeHighlighted = false;
    public StatsManager(AbstractWorldMap map, String title){
        String filepath = map.getName() + "-Stats-" + System.currentTimeMillis() / 1000 + ".csv";
        outputFile = new File(filepath);
        this.map = map;
        statsHist.add(new String[]{"Animals No.", "Number of plants", "Average Energy", "Average Lifespan", "Number Of Children"});
        UISetup(title);
    }

//    Sets up the charts and dominant genome UI elements
    private void UISetup(String title){
        for (StatsChart chart:allCharts) {
            chartsContainer.getChildren().add(chart.getUI());
        }
        mainContainer.setMaxHeight(Utils.windowHeight);
        mainContainer.setPadding(new Insets(10,0,30,0));
        mainContainer.setPrefWidth(Utils.windowWidth*0.16);
        mainContainer.setAlignment(Pos.CENTER);

        Text label = new Text(title);
        label.setTextAlignment(TextAlignment.CENTER);
        label.setFont(new Font(Utils.windowWidth * 0.013));

        Label dominantGenotypeLabel = new Label("Dominant genotype: ");
        dominantGenotypeLabel.setFont(new Font(Utils.windowWidth*0.007));
        mainContainer.getChildren().addAll(label, dominantGenotypeLabel, dominantGenotypeText, chartsContainer);
        mainContainer.setStyle("-fx-background-color: #eeb79b;");
    }

//    Adds relevant data to the charts
    public void chartUpdate(int epoch){
        Platform.runLater(()->{
            animalChart.addData(epoch, animalsNumber);
            plantChart.addData(epoch, plantNumber);
            energyChart.addData(epoch, energySum/Math.max(animalsNumber,1));
            lifespanChart.addData(epoch, lifetimeSum/lifetimeSamples);
            childrenChart.addData(epoch, childrenCountSum);
            statsHist.add(new String[]{
                    String.valueOf(animalsNumber),
                    String.valueOf(plantNumber),
                    String.valueOf(energySum/Math.max(animalsNumber,1)),
                    String.valueOf(lifetimeSum/lifetimeSamples),
                    String.valueOf(childrenCountSum)});
        });
    }
//    Resets stats that are recalcuated on each epoch
    public void resetEpochStats(){
        animalsNumber = 0;
        energySum = 0;
        childrenCountSum = 0;
    }
//    Reads data from an alive animal. Called each epoch
    public void readAliveAnimalData(Animal animal){
        animalsNumber += 1;
        childrenCountSum += animal.getChildren().size();
        energySum += animal.getEnergy();
    }

//    Reads data that is changed only when new Animal is spawned
    public void readDataOnAnimalBirth(Animal animal){
        //         Add the genotype to the hashmap and check if it's the new most populus
        int sameGenotypeCount = genotypes.get(animal.getGenome()) == null ? 0 : genotypes.get(animal.getGenome());
        genotypes.put(animal.getGenome(), sameGenotypeCount + 1);
        if (sameGenotypeCount + 1 > dominantGenotypeCount){
            dominantGenome = animal.getGenome();
            dominantGenotypeCount = sameGenotypeCount+1;
            Platform.runLater(()->dominantGenotypeText.setText(animal.getGenotypeString()));
        }
    }

//    Reads data that only needs to be read when an animal dies [*]
    public void readDataOnAnimalDeath(Animal animal, int epoch){
        lifetimeSum += epoch-animal.getBirthEpoch();
        lifetimeSamples += 1;
//        Remove the animals genotype from the hashmap
        int sameGenotypeCount = genotypes.get(animal.getGenome());
        genotypes.put(animal.getGenome(), sameGenotypeCount-1);
        if (sameGenotypeCount-1 == 0){
            genotypes.remove(animal.getGenome());
        }
        dominantGenotypeCount = -1;
//        Find the new dominant geotype
        for (Genome genome: genotypes.keySet()) {
            int genotypeCount = genotypes.get(genome);
            if (genotypeCount > dominantGenotypeCount){
                dominantGenotypeCount = genotypeCount;
                dominantGenome = genome;
            }
        }
//        dominantGenotypeText.setText(animal.getGenotypeString());
        Platform.runLater(() -> dominantGenotypeText.setText(animal.getGenotypeString()));
    }

//    Highlights all the animals with dominant genome
    public void highlightGenome(){
        deHighlightAll();
        this.genomeHighlighted = true;
        for (ArrayList<IMapElement> elements: map.getMapElements().values()) {
            for (IMapElement element:elements) {
                if (element instanceof Animal animal){
                    if (animal.getGenome().equals(dominantGenome)){
                        animal.highlight();
                    }

                }
            }
        }
    }

//    Disables the highlight on animals with dominant genome
    public void deHighlightAll(){
        this.genomeHighlighted = false;
        for (ArrayList<IMapElement> elements: map.getMapElements().values()) {
            for (IMapElement element:elements) {
                if (element instanceof Animal animal){
                    animal.deHighlight();
                }
            }
        }
    }

    public void addPlantCount(int number){
        plantNumber += number;
    }

    public void saveToFile(){
//        Save stats to file
        try (CSVWriter writer = new CSVWriter(new FileWriter(outputFile),';',
                CSVWriter.NO_QUOTE_CHARACTER,
                CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                CSVWriter.DEFAULT_LINE_END)) {
            int oldAvgIdx = -1;
            float[] averages = new float[5];
//            Sum for averages
            for (int i = 1; i < statsHist.size(); i++)
            {
                String[] stats = statsHist.get(i);
                if (Objects.equals(stats[0], "Average:")){
                    oldAvgIdx = i;
                    continue;
                }
                for (int j = 0; j < stats.length; j++) {
                    averages[j] += Float.parseFloat(stats[j]);
                }
            }
            //If saving again remove the old averages headers
            if (oldAvgIdx != -1){
                statsHist.remove(oldAvgIdx);
            }

//            Divide the sums
            for (int i = 0; i < averages.length; i++) {
                averages[i] = averages[i]/statsHist.size();
            }
//            Convert to string, add to array, write
            String[] averagesStr = new String[5];
            for (int i = 0; i < averages.length; i++) {
                averagesStr[i] = String.valueOf(averages[i]);
            }
            // Add average headers
            statsHist.add(new String[]{"Average:","Average:","Average:","Average:","Average:" });
            statsHist.add(averagesStr);
            writer.writeAll(statsHist);
        }catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Node getUI(){
        return this.mainContainer;
    }
    public boolean highlighted(){ return genomeHighlighted;}

}
