import PropTypes from "prop-types";

export default PropTypes.shape({
    id: PropTypes.string.isRequired,
    availabilityZone: PropTypes.string.isRequired,
});
