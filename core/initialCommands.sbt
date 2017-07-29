initialCommands in Compile in console :=
  """
  import flint._
  import flint.service._
  import flint.service.aws._
  import flint.service.mock._
  import java.io.File
  import com.typesafe.config.ConfigFactory

  import scala.concurrent.ExecutionContext.Implicits.global

  val flintConfig = ConfigFactory.parseFile(new File("conf/aws_service.conf")).getConfig("flint")
  validateFlintConfig(flintConfig)

  val awsService: ClusterService = new AwsClusterService(flintConfig)
  val mockService: ClusterService = new MockClusterService
  """
