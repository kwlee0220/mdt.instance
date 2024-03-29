package mdt.registry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportResource;

@SpringBootApplication
@ImportResource("classpath:applicationContext.xml")
public class Application implements Runnable {
//	@Option(names={"--store_dir"}, paramLabel="path", required=true, description={"grid expression"})
//	private File m_storeDir;

	public static void main(String[] args) {
//        new CommandLine(new Application()).execute(args);
        
        SpringApplication.run(Application.class, args);
	}
	
    @Override
    public void run() {
    	System.out.println();
    }
}
