# PATRONES USED #

## 1. Abstract Factory
Location: `FabricaProductoParametrico`, `FabricaSequia`, `FabricaExceso`, and `FabricaHelada`.

Purpose: create a complete and compatible family for each parametric product.
Each factory creates its own index source, trigger rule, certificate generator, and premium rate.
For example, `FabricaHelada` creates the frost rule and `CERT-HEL` certificate together.
The liquidation code only uses the factory interfaces, so it cannot combine the drought
source with the frost rule by mistake.

## 2. Factory Method
Location: abstract class `CanalDeVenta` and the protected method
`crearComprobante` implemented by `CanalCooperativa`, `CanalAppMovil`, and
`CanalCorresponsal`.

Purpose: create the receipt according to the sales channel.
The public method `emitir` contains the common work: it calculates the commission,
assigns the independent sequence number, and requests the receipt. Each subclass only
decides its own number format, commission, and printed content.

## 3. Builder
Location: `Poliza.Builder` inside `Main.java`.

Purpose: construct a policy with many required and optional values in a readable way.
`Poliza` is immutable because its fields are final and its constructor is private.
The `build()` method validates required fields, the 0.5 to 20 hectare limit, the minimum
60-day coverage period, and the requirement for a previous policy during renewal.

## Execution

From the project folder run:

```text
javac Main.java
java Main
```
