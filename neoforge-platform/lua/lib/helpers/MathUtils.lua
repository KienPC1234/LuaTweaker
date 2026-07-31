-- ===================================================================
-- LuaTweaker Shared Math Helper Library (lib/helpers/MathUtils.lua)
-- ===================================================================

local MathUtils = {}

--- Calculates Euclidean distance between two positions
---@param posA any
---@param posB any
---@return number
function MathUtils.calculateDistance(posA, posB)
    if not posA or not posB then return 0 end
    local dx = (posA.X or posA.x or 0) - (posB.X or posB.x or 0)
    local dy = (posA.Y or posA.y or 0) - (posB.Y or posB.y or 0)
    local dz = (posA.Z or posA.z or 0) - (posB.Z or posB.z or 0)
    return math.sqrt(dx * dx + dy * dy + dz * dz)
end

print("[MathUtils] Shared Math Library loaded successfully from lib/helpers/MathUtils.lua!")

return MathUtils
