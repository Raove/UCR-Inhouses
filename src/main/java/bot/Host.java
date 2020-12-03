package bot;

import com.opencsv.bean.CsvBindByName;

public class Host {

    @CsvBindByName(column = "id")
    private String id;

    @CsvBindByName(column = "hosts")
    private int hosts;

    public Host()
    {

    }

    Host(String _id)
    {
        id = _id;
        hosts = 0;
    }

    public String getId()
    {
        return id;
    }

    public int getHosts()
    {
        return hosts;
    }

    public void increment()
    {
        ++hosts;
    }
}
