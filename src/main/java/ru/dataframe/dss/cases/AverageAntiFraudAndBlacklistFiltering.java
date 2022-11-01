package ru.dataframe.dss.cases;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import ru.dataframe.dss.average.AverageAccumulator;
import ru.dataframe.dss.average.AverageFraudCheckingFunction;
import ru.dataframe.dss.blacklist.FilterBlacklistedFunction;
import ru.dataframe.dss.dto.BlacklistItem;
import ru.dataframe.dss.dto.Transaction;
import ru.dataframe.dss.serialization.TransactionDeserializationSchema;
import ru.dataframe.dss.utils.ConfigLoader;
import ru.dataframe.dss.utils.FlinkProvider;

import java.util.List;
import java.util.Properties;


public class AverageAntiFraudAndBlacklistFiltering {

	public static void main(String[] args) throws Exception {
		Properties config = ConfigLoader.load("average_config.properties");
		List<BlacklistItem> blacklist = BlacklistItem.getBlacklist("blacklist.txt");

		String sourceTopic = config.getProperty("source.topic");
		String sourceBootstrapServer = config.getProperty("source.bootstrap-server");
		String sinkTopic = config.getProperty("sink.topic");
		String sinkBootstrapServer = config.getProperty("sink.bootstrap-server");
		Time windowDuration = Time.seconds(Long.parseLong(config.getProperty("window-duration")));

		StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

		DataStream<Transaction> dataStream = FlinkProvider.getDataStream(env,
				sourceTopic,
				sourceBootstrapServer,
				new TransactionDeserializationSchema(),
				WatermarkStrategy.<Transaction>forMonotonousTimestamps()
						.withTimestampAssigner((transaction, timestamp) -> transaction.getEventTime()),
				"Transaction Source"
		);

		dataStream.keyBy(Transaction::getClientId)
				.process(new FilterBlacklistedFunction(blacklist))
				.keyBy(Transaction::getClientId)
				.process(new AverageFraudCheckingFunction(windowDuration, new AverageAccumulator()))
				.map(String::valueOf)
				.sinkTo(FlinkProvider.getSink(sinkTopic,
						sinkBootstrapServer,
						new SimpleStringSchema()
				));

		env.execute();
	}
}