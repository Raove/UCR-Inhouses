package bot;

import com.opencsv.CSVWriter;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import net.dv8tion.jda.api.entities.Member;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class HostTracker {
    private static final String fileName = "hosts.csv";
    //private static final String fileName = "hoststest.csv";
    private List<Host> hosts;

    HostTracker()
    {
        try
        {
            Reader reader = Files.newBufferedReader(Paths.get(fileName));
            CsvToBean<Host> parser = new CsvToBeanBuilder<Host>(reader)
                    .withType(Host.class)
                    .withIgnoreLeadingWhiteSpace(true)
                    .build();

            hosts = parser.parse();

            reader.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    boolean exists(String id)
    {
        for (Host host : hosts)
            if (host.getId().equals(id))
                return true;

        return false;
    }

    void addHostToTracker(Host host)
    {
        hosts.add(host);
    }

    List<Host> getHosts()
    {
        return hosts;
    }

    void write()
    {
        try
        {
            System.out.println("Updating hosts list");
            Writer writer = Files.newBufferedWriter(Paths.get(fileName));
            StatefulBeanToCsv<Host> beanToCsv = new StatefulBeanToCsvBuilder<Host>(writer)
                    .withQuotechar(CSVWriter.NO_QUOTE_CHARACTER)
                    .build();
            beanToCsv.write(hosts);
            writer.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
