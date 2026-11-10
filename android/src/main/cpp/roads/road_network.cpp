#include "road_network.h"
#include <algorithm>
#include <cmath>
#include <limits>
#include <unordered_set>
#include <utility>

namespace oe { namespace roads {
namespace {
constexpr double kMaxCoordinate=8192.0; // current OE editor world: 16 km
constexpr double kMaxRoadLength=25000.0;
constexpr double kMinSegmentLength=0.01;
constexpr double kYOffset=0.14; // above painted layers and PNG guide
constexpr double kTextureRepeatMetres=2.0;
bool finite(double v) noexcept { return std::isfinite(v); }
double length(Point p) noexcept { return std::hypot(p.x,p.z); }
Point subtract(Point a,Point b) noexcept { return {a.x-b.x,a.z-b.z}; }
Point add(Point a,Point b) noexcept { return {a.x+b.x,a.z+b.z}; }
Point multiply(Point p,double s) noexcept { return {p.x*s,p.z*s}; }
Point perpendicular(Point p) noexcept { return {-p.z,p.x}; }
Point normalized(Point p) noexcept {
    const double l=length(p);
    return l>1.e-12?multiply(p,1.0/l):Point{};
}
double dot(Point a,Point b) noexcept { return a.x*b.x+a.z*b.z; }
bool validPoint(Point p) noexcept {
    return finite(p.x)&&finite(p.z)&&std::abs(p.x)<=kMaxCoordinate&&
           std::abs(p.z)<=kMaxCoordinate;
}
bool sampledVertex(Point p,double u,double v,const TerrainHeight& height,
                   Vertex& out) {
    if(!validPoint(p))return false;
    const double y=height(p.x,p.z);
    if(!finite(y)||std::abs(y)>10000.0)return false;
    out={static_cast<float>(p.x),static_cast<float>(y+kYOffset),
         static_cast<float>(p.z),static_cast<float>(u),static_cast<float>(v)};
    return true;
}
struct Sample { Point center; double distance=0.0; };
} // namespace

bool validRoad(const Road& road) noexcept {
    if(!road.id||road.name.size()>128||road.material.empty()||
       road.material.size()>128||!finite(road.widthMetres)||
       road.widthMetres<kMinRoadWidth||road.widthMetres>kMaxRoadWidth||
       (!finite(road.tileMetres)||road.tileMetres<0.5||road.tileMetres>32.0)||
       road.controlPoints.size()<2||road.controlPoints.size()>kMaxControlPoints)
        return false;
    double travelled=0.0;
    for(std::size_t i=0;i<road.controlPoints.size();++i){
        if(!validPoint(road.controlPoints[i]))return false;
        if(i){
            const double segment=length(subtract(road.controlPoints[i],road.controlPoints[i-1]));
            if(!finite(segment)||segment<kMinSegmentLength)return false;
            travelled+=segment;
            if(travelled>kMaxRoadLength)return false;
        }
    }
    return true;
}

bool buildSurfaceMesh(const Road& road,const TerrainHeight& height,
                      SurfaceMesh& output,double samplingStepMetres){
    if(!validRoad(road)||!height||!finite(samplingStepMetres)||
       samplingStepMetres<0.25||samplingStepMetres>16.0)return false;
    std::vector<Sample> samples;
    samples.reserve(std::min<std::size_t>(kMaxMeshSamples,road.controlPoints.size()*8));
    samples.push_back({road.controlPoints.front(),0.0});
    double travelled=0.0;
    for(std::size_t i=1;i<road.controlPoints.size();++i){
        const Point start=road.controlPoints[i-1],end=road.controlPoints[i];
        const double segment=length(subtract(end,start));
        const std::size_t steps=static_cast<std::size_t>(std::ceil(segment/samplingStepMetres));
        if(steps==0||steps>kMaxMeshSamples||
           samples.size()+steps>kMaxMeshSamples)return false;
        for(std::size_t n=1;n<=steps;++n){
            const double alpha=static_cast<double>(n)/steps;
            const Point pos=add(start,multiply(subtract(end,start),alpha));
            samples.push_back({pos,travelled+segment*alpha});
        }
        travelled+=segment;
    }
    const double half=road.widthMetres*0.5;
    std::vector<Vertex> left,right;
    left.reserve(samples.size());right.reserve(samples.size());
    for(std::size_t i=0;i<samples.size();++i){
        const Point current=samples[i].center;
        const Point before=i? samples[i-1].center:current;
        const Point after=i+1<samples.size()?samples[i+1].center:current;
        const Point dirA=normalized(subtract(current,before));
        const Point dirB=normalized(subtract(after,current));
        Point normal=i==0?perpendicular(dirB):perpendicular(dirA);
        double miter=half;
        if(i>0&&i+1<samples.size()){
            const Point nA=perpendicular(dirA),nB=perpendicular(dirB);
            const Point blended=normalized(add(nA,nB));
            if(length(blended)>0.0){
                normal=blended;
                const double denom=std::abs(dot(blended,nB));
                // Clamp acute corners to prevent extremely long miter spikes.
                miter=std::min(half*1.75,half/std::max(denom,0.2));
            }
        }
        const Point l=add(current,multiply(normal,miter));
        const Point r=add(current,multiply(normal,-miter));
        Vertex lv,rv;
        const double v=samples[i].distance/kTextureRepeatMetres;
        if(!sampledVertex(l,0.0,v,height,lv)||
           !sampledVertex(r,road.widthMetres/kTextureRepeatMetres,v,height,rv))
            return false;
        left.push_back(lv);right.push_back(rv);
    }
    SurfaceMesh result;
    result.triangles.reserve((samples.size()-1)*6);
    for(std::size_t i=1;i<samples.size();++i){
        // CCW Y-up winding (the renderer may enable back-face culling).
        const Vertex& l0=left[i-1],&r0=right[i-1],&l1=left[i],&r1=right[i];
        result.triangles.push_back(l0);
        result.triangles.push_back(l1);
        result.triangles.push_back(r0);
        result.triangles.push_back(l1);
        result.triangles.push_back(r1);
        result.triangles.push_back(r0);
    }
    output=std::move(result);
    return true;
}

std::uint64_t Network::create(std::string name,std::string material,
                              double widthMetres,std::vector<Point> points){
    if(roads_.size()>=kMaxRoads||nextId_==0||
       nextId_==std::numeric_limits<std::uint64_t>::max())return 0;
    Road candidate{nextId_,std::move(name),std::move(material),
                   widthMetres,std::move(points)};
    if(!validRoad(candidate))return 0;
    roads_.push_back(std::move(candidate));
    return nextId_++;
}

const Road* Network::find(std::uint64_t id) const noexcept {
    for(const auto& road:roads_)if(road.id==id)return &road;
    return nullptr;
}

bool Network::update(const Road& updated){
    if(!validRoad(updated))return false;
    for(auto& road:roads_)if(road.id==updated.id){
        road=updated;
        return true;
    }
    return false;
}

bool Network::remove(std::uint64_t id) noexcept {
    const auto it=std::find_if(roads_.begin(),roads_.end(),
                              [id](const Road& road){return road.id==id;});
    if(it==roads_.end())return false;
    roads_.erase(it);
    return true;
}

bool Network::replaceAll(const std::vector<Road>& incoming){
    if(incoming.size()>kMaxRoads)return false;
    std::unordered_set<std::uint64_t> ids;
    std::uint64_t maximum=0;
    for(const auto& road:incoming){
        if(!validRoad(road)||!ids.insert(road.id).second)return false;
        maximum=std::max(maximum,road.id);
    }
    if(maximum==std::numeric_limits<std::uint64_t>::max())return false;
    std::vector<Road> replacement(incoming);
    roads_.swap(replacement);
    nextId_=std::max(nextId_,maximum+1);
    return true;
}

}} // namespace oe::roads
