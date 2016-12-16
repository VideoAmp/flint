import React from 'react';

import NumberInput from 'material-ui-number-input';
import {Card, CardActions, CardHeader, CardText} from 'material-ui/Card';
import Dialog from 'material-ui/Dialog';
import FlatButton from 'material-ui/FlatButton';
import Trash from 'material-ui/svg-icons/action/delete';
import Add from 'material-ui/svg-icons/content/add';

import Instance from './Instance';

const generateInstance =
    (instance, master = false) => <Instance key={instance.id} data={instance} master={master} />

export default class Cluster extends React.Component {
    state = {
        instanceDialogOpen: false,
    };

    handleInstanceDialogOpen = () => {
        this.setState({ instanceDialogOpen: true });
    };

    handleInstanceDialogClose = () => {
        this.setState({ instanceDialogOpen: false });
    };

    onInstanceCountError = (error) => {
        var errorText = (error === "none") ?
            "" :
            "Please enter a valid instance count (less than 100)";

        this.setState({ errorText });
    };

    render() {
        const instanceDialogActions = [
            <FlatButton
                label="Cancel"
                onTouchTap={this.handleInstanceDialogClose}
            />,
            <FlatButton
                label="Launch"
                primary={true}
                onTouchTap={this.handleInstanceDialogClose}
            />,
        ];
        const {owner, dockerImage, master, workers=[]} = this.props.data;

        // TODO: return short-form image tag
        const clusterTitle =
            `${owner} ${dockerImage.tag.split("-")[0]}`

        return (
            <div>
                <Card>
                    <CardHeader
                        title={clusterTitle}
                        titleStyle={{ "fontSize": "125%"}}
                    >
                        <Trash/>
                        <Add onTouchTap={this.handleInstanceDialogOpen}/>
                    </CardHeader>
                    <CardText>
                        { master ? generateInstance(master, true) : null }
                        { workers.map(generateInstance) }
                    </CardText>
                    <CardActions style={{ "backgroundColor": "#ccc"}}>
                        <p>2 cores, 2GB RAM, $0.039/hr</p>
                    </CardActions>
                </Card>
                <Dialog
                    title="Add Worker"
                    actions={instanceDialogActions}
                    modal={false}
                    open={this.state.instanceDialogOpen}
                    onRequestClose={this.handleInstanceDialogClose}
                >
                    <NumberInput
                        id="instance-amount-input"
                        floatingLabelText="Instance Count"
                        defaultValue={1}
                        min={1}
                        max={100}
                        strategy="allow"
                        errorText={this.state.errorText}
                        onError={this.onInstanceCountError}
                    />
                </Dialog>
            </div>
        );
    }
}
