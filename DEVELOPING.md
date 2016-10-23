## Developing for Flint

This documentation is for developers who wish to hack on the Flint codebase itself.

### Basic Concepts

(Cluster, master, worker, identifiers, cluster service, etc.)

### Building Flint

Flint is built with [sbt](http://www.scala-sbt.org/). We recommend using the sbt runner from [sbt-extras](https://github.com/paulp/sbt-extras), but the standard runner will do.

In addition to `compile` and `test`, the most important sbt tasks for development are `scalafmt` and `scalastyle`. The former runs the [scalafmt](http://scalafmt.org/) code formatter, and the latter runs the [scalastyle](http://scalastyle.org/) style checker. Scalastyle is automatically run as part of compilation, and compilation will fail if scalastyle finds any errors.

### Editors and IDEs

Flint is an editor-agnostic codebase. Please do not commit any tool-specific files.

### Project Structure

Flint is separated into two sbt projects: _core_ and _server_. The _core_ project provides everything needed to use Flint as a library. The _server_ project adds a WebSocket server for remote interaction.

### Configuration

_TODO_

### Client/Server Development

#### Communication Protocol

_TODO_

#### Message Schema

Flint protocol messages are JSON objects. They are divided into two classes, based on the origin of a message: _server messages_ are sent by the server, and _client messages_ are sent by remote clients.

The server project includes a JSON schema generator for all Flint protocol messages, `MessageSchemaGenerator`. It can be run via sbt: `server/schema:run`. By default, `MessageSchemaGenerator` prints the complete protocol schemata to standard out. Its behavior can be modified with flags:

* `-s`: output server message schemata
* `-c`: output client message schemata
* `-f <filename>`: write output to `filename`

The server project also includes a program, `TestMessages`, which prints examples of each kind of Flint message. Run it with `server/test:run`.

#### Mock Cluster Service

Flint provides a mock cluster service for development: `flint.server.mock.MockClusterService`. You can run a Flint server with the mock cluster service by setting the `flint.server.cluster_service` configuration option to `mock`. The mock cluster simulates a cluster environment, responding to client requests and simulating instance and cluster lifecycles.
