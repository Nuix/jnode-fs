import lznt1

# Define the file path
file_path = './THE TRAGEDY OF HAMLET.txt'

# Read the file and store its contents in a byte array
with open(file_path, 'rb') as file:
    byte_array = bytearray(file.read())
    
# Data to compress
#data = b"This is some data to compress using LZNT1."

# Compress the data
compressed_data = lznt1.compress(byte_array)

# Define the output file path
compressed_file_path = 'compressed.bin'

# Write the byte array to the file
with open(compressed_file_path, 'wb') as compressed_file:
    compressed_file.write(compressed_data)


# Decompress the data
decompressed_data = lznt1.decompress(compressed_data)

# Define the output file path
decompressed_file_path = 'decompressed.bin'

# Write the byte array to the file
with open(decompressed_file_path, 'wb') as decompressed_file:
    decompressed_file.write(decompressed_data)