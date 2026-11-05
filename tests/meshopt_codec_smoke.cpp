// Offline round-trip verification for vendored meshoptimizer decoder.
#include "../android/src/main/cpp/vendor/meshoptimizer/meshoptimizer.h"
#include <cassert>
#include <cstdio>
#include <cstring>
#include <vector>
int main(){
 const float verts[4][3]={{0,0,0},{1,0,0},{0,1,0},{1,1,0}};
 std::vector<unsigned char> encoded(meshopt_encodeVertexBufferBound(4,12));
 const size_t size=meshopt_encodeVertexBuffer(encoded.data(),encoded.size(),verts,4,12);
 assert(size>0);
 float decoded[4][3]={{0}};
 assert(meshopt_decodeVertexBuffer(decoded,4,12,encoded.data(),size)==0);
 assert(std::memcmp(decoded,verts,sizeof(verts))==0);
 const unsigned int indices[6]={0,1,2,2,1,3};
 std::vector<unsigned char> packed(meshopt_encodeIndexBufferBound(6,4));
 const size_t packedSize=meshopt_encodeIndexBuffer(packed.data(),packed.size(),indices,6);
 assert(packedSize>0);
 unsigned int indicesRestored[6]={0};
 assert(meshopt_decodeIndexBuffer(indicesRestored,6,4,packed.data(),packedSize)==0);
 assert(std::memcmp(indicesRestored,indices,sizeof(indices))==0);
 std::vector<unsigned char> sequence(meshopt_encodeIndexSequenceBound(6,4));
 const size_t sequenceSize=meshopt_encodeIndexSequence(sequence.data(),sequence.size(),indices,6);
 assert(sequenceSize>0);
 unsigned int sequenceRestored[6]={0};
 assert(meshopt_decodeIndexSequence(sequenceRestored,6,4,sequence.data(),sequenceSize)==0);
 assert(std::memcmp(sequenceRestored,indices,sizeof(indices))==0);
 // Corrupt input is rejected, never accepted as an empty geometry.
 assert(meshopt_decodeVertexBuffer(decoded,4,12,encoded.data(),1)!=0);
 std::puts("OE_MESHOPT_CODEC_OK");
 return 0;
}
