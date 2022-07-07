# Netlogo Extension Language Server Library
## A unified backend for writing NetLogo extensions that provide interoperability with other programming languages

```
┌────────────────┐                   ┌───────────┐  JSON-encoded commands  ┌───────────────────┐
│                │  Calls into       │           │  over TCP Connection    │                   │
│                ├──────────────────►│           ├────────────────────────►│                   │
│ Extension code │                   │  Library  │                         │  Target language  │
│                │◄──────────────────┤           │◄────────────────────────┤  server           │
│                │  Returns NetLogo  │           │  JSON-encoded return    │                   │
└────────────────┘  Objects          └───────────┘  data                   └───────────────────┘
```
<!-- https://asciiflow.com/#/share/eJytU8FOwzAM%2FRXL5%2B3Cha3XiguaNgQ75uK1VlWUOlKSolbTbvsEBP%2BC%2BBq%2BhHYH1NJGVGXROySOn%2BNnO0cUKhgjKbVeoKaaLUZ4VFgpjFbr24XCutndrNbNznPlm4PCr9fPGYDhmsy8f9ptlyyJSTmFxBQFSeom80NQSkbyuphi0tpBLt50rX0f88IW9vEDxEaEE58bGXp2GMHnZuLtY5DU1fA79lDGXeVZXKu57UpYNcAmP1iydagyHc892Yw9aJKspIz%2FqNr7eba%2B60Uai%2B3YtqMxrfkAj%2BxLKw627DcmM6Oz1vsA9kL4uQs3aVb2u8NzM8yuX6FJzJQ8jafzHyg84ekbveJbHw%3D%3D -->

An extension using this library has three parts:
1. The extension itself, which registers primitives that call into this library
2. The library which forwards target language commands and encoded NetLogo data structures to the target language server
3. A tcp server on localhost written in the target language which listens for messages from the library and `eval`'s them and gives results back to the library.


## Examples:
The [CCL official NodeJS JavaScript extension](https://github.com/NetLogo/NodeJS-Extension) is written with this library and should be used as a template.

The [CCL official Python extension](https://github.com/NetLogo/Python-Extension) is also written with this library, but has more surrounding code in order to maintain backwards compatibility.

The [CCL SimpleR extension](https://github.com/NetLogo/SimpleR-Extension) for interoperating with the R language and platform is also written with this library.  It serves as a good example as to how this library can simplify the creation of these sorts of extensions when compared to the [old R extension](https://github.com/NetLogo/R-Extension).

## How to write the extension code:

The extension code has to:
* Manage starting and stopping the subprocess by calling into this library
* Register extension primitives that call into the library
* Optionally, manage the lifecycle of the pop-out-shell functionality provided by the library

## How to write the language-specific code

The language specific code has to:
* Create a tcp server on localhost
* listen for messages from the NetLogo extension
* Execute/evaluate the code coming in those messages using something like `eval`
* Return the results back to the extension
* Give error information if anything goes wrong or if the code passed in throws any exceptions

Input messages have 4 types, statements, expressions, assignment, and stringified expressions. Each message type is associated with an integer, 0 to 3 respectively. Statements should be executed, expressions should be evaluated (and return a result), assignments should assign the result of an evaluation to a variable with the given name, and stringified expressions should be evaluated, but converted into a helpful string representation before being returned to the extension

Input messages are of the form:
```
{
    "type" : Int,
    "body" : String | Object
}
```
where body is either a string of js code to be evaluated/run or, in the
case of assignment, an object of the form:
```
{
    "varName" : String,
    "value" : String
}
```
where value is a string of js code to be evaluated/run.

Output messages have two types, success and failure. Failure should be
accompanied by a cause of the failure. They have the form:
```
{
    "type" : Int,
    "body" : String | Object
}
```
where body is always a string if the type is Success (0) and the following
object when the type is Error (1)
```
{
    "message" : String,
    "cause" : String
}
```
Where message is a short error message and cause is a longer, more detailed
message, perhaps with a stack trace if one is available.

### Port numbers
During the initialization of the 'Subprocess' object, you have the choice of manually specifying a port on localhost
over which the communication should happen or letting the target language server choose one itself. If you do let the
target language server choose one, it needs to emit it as the very first line of stdout.

## A full cycle:
1. The user calls an extension primitive. This example will use `my-extension:set "foo" patch 0 0`
2. The extension code forwards that to the library's corresponding method, in this case `mySubprocess.assign(varName: String, value: AnyRef)` method
3. If needed, the library code serializes the NetLogo value into a JSON representation. For numbers, strings, and lists, this is trivial. Agents (turtles, links, patches) are converted into JSON objects with key-value pairs for each of their owned variables.  Agentsets are converted into lists of JSON-encoded agents.
4. The library wraps the command into the following json-encoded message and sends it to the TCP server running in the target language's interpreter
```
{
    "type" : 2,
    "body" : {
        "varName" : "foo",
        "value" : {
            "pxcor": 0,
            "pycor": 0,
            "pcolor": 0,
            "plabel": "",
            "plabel-color": 9.9
        }
    }
}
```
5. The target language TCP server receives the message and performs the requested operation, in this case setting the variable "foo" to the json-serialized representation of the patch.
6. The target language TCP server sends the following message back to the library's code to signify the assignment was processed successfully.
```
{
    "type": 0,
    "body": ""
}
```
7. The library finishes up. In the case of eval, it converts the JSON-encoded `body` field back into a NetLogo data type. This is done trivially for numbers and strings. JSON objects are converted into a list of key-value two-item lists.
8. Control returns to the extension code.
