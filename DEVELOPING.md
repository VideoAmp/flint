## Developing for Flint

This documentation is for developers who wish to hack on the Flint codebase itself.

### Project Structure

Flint is consists of two sbt projects—_core_ and _server_—along with a [React](https://facebook.github.io/react/) app in the ui subdirectory. The _core_ project provides everything needed to use Flint as a library. The _server_ project adds a WebSocket server for remote interaction. See [ui/README.md](ui/README.md) for more information on the ui.

### Basic Concepts

(Cluster, master, worker, identifiers, cluster service, etc.)

### Building Flint

Flint is built with [sbt](http://www.scala-sbt.org/). We recommend using the sbt runner from [sbt-extras](https://github.com/paulp/sbt-extras), but the standard runner will do.

In addition to `compile` and `test`, the most important sbt tasks for development are `scalafmt` and `scalastyle`. The former runs the [scalafmt](http://scalafmt.org/) code formatter, and the latter runs the [scalastyle](http://scalastyle.org/) style checker. Scalastyle is automatically run as part of compilation, and compilation will fail if scalastyle finds any errors.

### Publishing the Core Library to Maven Central

Run `sbt core/publish`.

### Publishing the Core API Documentation

Run `sbt core/ghpagesPushSite`.

### Building the Docker Images

The build system supports building two Docker images: a server-only image and an app image including both server and UI. They are both built with the [sbt-docker](https://github.com/marcuslonnberg/sbt-docker) plugin. To build the server-only image, run `sbt server/docker`. To build the app image, run `sbt docker`. Likewise, use the `dockerBuildAndPush` sbt task to push each image.

### Editors and IDEs

Flint is an editor-agnostic codebase. Please do not commit any tool-specific files, including tool-specific `.gitignore` entries. Put your local excludes in `.git/info/exclude`.

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

Flint provides a mock cluster service for development: `flint.service.mock.MockClusterService`. You can run a Flint server with the mock cluster service by setting `cluster_service="mock"` in `server.conf`. The mock cluster simulates a cluster environment, responding to client requests and simulating instance and cluster states. It supports most functionality of a full cluster service with the notable exception of spot instances.
