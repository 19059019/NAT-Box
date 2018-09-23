![NAT logo](NAT_blue.jpg)

# Project 3 - Network Address Translation

## Group Members:

Michael John Shepherd   - 19059019 at sun dot ac dot za

Martin William von Fintel       - 20058837 at sun dot ac dot za

## File Contents:

**NAT** contains the source code for the project.

**Report** contains the project report.

## Usage:

The project package can be found by navigating to the **src** folder:

```
cd NAT/NetworkAddressTranslation/src

```
The project can the be compiled with the Makefile, using the following command:
```
make
```
The NATBox simulator can then be run with the following command:
```
java networkaddresstranslation.NATBox <Table Refresh Time>
```
Clients can the be spawned with the following command:
```
java networkaddresstranslation.Client <host> 8000 <Internal(0)/External(1)>
```
To send messages between clients, type the following into console:
```
send <Destination IP>
```
